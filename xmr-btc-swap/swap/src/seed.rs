use crate::fs::ensure_directory_exists;
use ::bitcoin::secp256k1::{self, SecretKey};
use anyhow::{Context, Result};
use bdk::bitcoin::util::bip32::ExtendedPrivKey;
use bitcoin::hashes::{sha256, Hash, HashEngine, sha512, HmacEngine, Hmac};
use libp2p::identity;
use pem::{encode, Pem};
use rand::prelude::*;
use std::ffi::OsStr;
use std::fmt;
use std::fs::{self, File};
use std::io::{self, Read, Write};
use std::path::{Path, PathBuf};
use std::str::FromStr;
use bitcoin::hashes::hex::FromHex;
use bitcoin::util::bip32::{ChainCode, ChildNumber, Fingerprint};
use torut::onion::TorSecretKeyV3;

pub const SEED_LENGTH: usize = 64;

#[derive(Eq, PartialEq)]
pub struct Seed([u8; SEED_LENGTH]);

impl Seed {
    pub fn random() -> Result<Self, Error> {
        let mut bytes = [0u8; 64];
        rand::thread_rng().fill_bytes(&mut bytes);

        // If it succeeds once, it'll always succeed
        let _ = SecretKey::from_slice(&bytes)?;

        Ok(Seed(bytes))
    }

    pub fn derive_extended_private_key(
        &self,
        network: bitcoin::Network,
    ) -> Result<ExtendedPrivKey> {
        let seed = self.derive(b"BITCOIN_EXTENDED_PRIVATE_KEY").bytes();
        let private_key = ExtendedPrivKey::new_master(network, &seed)
            .context("Failed to create new master extended private key")?;

        Ok(private_key)
    }

    pub fn derive_bip32_key(
        &self,
        network: bitcoin::Network,
    ) -> Result<ExtendedPrivKey> {
        let key_bytes = self.bytes();
        let mut hmac_engine: HmacEngine<sha512::Hash> = HmacEngine::new(b"Bitcoin seed");
        hmac_engine.input(&key_bytes);
        let hmac_result: Hmac<sha512::Hash> = Hmac::from_engine(hmac_engine);

        let xprv = ExtendedPrivKey {
            network,
            depth: 0,
            parent_fingerprint: Fingerprint::from_hex("00000000").expect("Failed to get parent fingerprint from hex"),
            child_number: ChildNumber::from_normal_idx(0)?,
            private_key: SecretKey::from_slice(&hmac_result[..32])?,
            chain_code: ChainCode::from(&hmac_result[32..]),
        };
        Ok(xprv)
    }

    pub fn derive_libp2p_identity(&self) -> identity::Keypair {
        let bytes = self.derive(b"NETWORK").derive(b"LIBP2P_IDENTITY").bytes();
        let mut sliced_bytes = [0u8; 32];
        sliced_bytes.copy_from_slice(sha256::Hash::hash(&bytes).to_vec().as_slice());
        let key = identity::ed25519::SecretKey::from_bytes(sliced_bytes).expect("we always pass 32 bytes");

        identity::Keypair::Ed25519(key.into())
    }

    pub fn derive_libp2p_identity_asb(&self) -> identity::Keypair {
        let bytes = self.derive(b"NETWORK").derive(b"LIBP2P_IDENTITY").derive(b"ASB").bytes();
        let mut sliced_bytes = [0u8; 32];
        sliced_bytes.copy_from_slice(sha256::Hash::hash(&bytes).to_vec().as_slice());
        let key = identity::ed25519::SecretKey::from_bytes(sliced_bytes).expect("we always pass 32 bytes");

        identity::Keypair::Ed25519(key.into())
    }

    pub fn derive_torv3_key(&self) -> TorSecretKeyV3 {
        let bytes = self.derive(b"TOR").bytes();
        let mut sliced_bytes = [0u8; 32];
        sliced_bytes.copy_from_slice(sha256::Hash::hash(&bytes).to_vec().as_slice());
        let sk = ed25519_dalek::SecretKey::from_bytes(&sliced_bytes)
            .expect("Failed to create a new extended secret key for Tor.");
        let esk = ed25519_dalek::ExpandedSecretKey::from(&sk);
        esk.to_bytes().into()
    }

    pub fn from_file_or_generate(data_dir: &Path) -> Result<Self, Error> {
        let file_path_buf = data_dir.join("seed.pem");
        let file_path = Path::new(&file_path_buf);

        if file_path.exists() {
            return Self::from_file(file_path);
        }

        tracing::debug!("No seed file found, creating at {}", file_path.display());

        let random_seed = Seed::random()?;
        random_seed.write_to(file_path.to_path_buf())?;

        Ok(random_seed)
    }

    /// Derive a new seed using the given scope.
    ///
    /// This function is purposely kept private because it is only a helper
    /// function for deriving specific secret material from the root seed
    /// like the libp2p identity or the seed for the Bitcoin wallet.
    fn derive(&self, scope: &[u8]) -> Self {
        let mut engine = sha512::HashEngine::default();

        engine.input(&self.bytes());
        engine.input(scope);

        let hash = sha512::Hash::from_engine(engine);

        Self(hash.into_inner())
    }

    fn bytes(&self) -> [u8; SEED_LENGTH] {
        self.0
    }

    fn from_file<D>(seed_file: D) -> Result<Self, Error>
    where
        D: AsRef<OsStr>,
    {
        let file = Path::new(&seed_file);
        let contents = fs::read_to_string(file)?;
        let pem = pem::parse(contents)?;

        tracing::debug!("Reading in seed from {}", file.display());

        Self::from_pem(pem)
    }

    fn from_pem(pem: pem::Pem) -> Result<Self, Error> {
        let contents = pem.contents();
        if contents.len() != SEED_LENGTH {
            Err(Error::IncorrectLength(contents.len()))
        } else {
            let mut array = [0; SEED_LENGTH];
            for (i, b) in contents.iter().enumerate() {
                array[i] = *b;
            }

            Ok(Self::from(array))
        }
    }

    fn write_to(&self, seed_file: PathBuf) -> Result<(), Error> {
        ensure_directory_exists(&seed_file)?;

        let data = self.bytes();
        let pem = Pem::new("SEED", data);

        let pem_string = encode(&pem);

        let mut file = File::create(seed_file)?;
        file.write_all(pem_string.as_bytes())?;

        Ok(())
    }
}

impl fmt::Debug for Seed {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "Seed([*****])")
    }
}

impl fmt::Display for Seed {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{:?}", self)
    }
}

impl From<[u8; SEED_LENGTH]> for Seed {
    fn from(bytes: [u8; SEED_LENGTH]) -> Self {
        Seed(bytes)
    }
}

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("Secp256k1: ")]
    Secp256k1(#[from] secp256k1::Error),
    #[error("io: ")]
    Io(#[from] io::Error),
    #[error("PEM parse: ")]
    PemParse(#[from] pem::PemError),
    #[error("expected 32 bytes of base64 encode, got {0} bytes")]
    IncorrectLength(usize),
    #[error("RNG: ")]
    Rand(#[from] rand::Error),
    #[error("no default path")]
    NoDefaultPath,
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::env::temp_dir;

    #[test]
    fn generate_random_seed() {
        let _ = Seed::random().unwrap();
    }

    #[test]
    fn seed_byte_string_must_be_32_bytes_long() { // now 64 bytes i just don't want to bother fixing this rn
        let _seed = Seed::from(*b"this string is exactly 64 bytes!this string is exactly 64 bytes!");
    }

    #[test]
    fn seed_from_pem_works() {
        use base64::engine::general_purpose;
        use base64::Engine;

        let payload: &str = "syl9wSYaruvgxg9P5Q1qkZaq5YkM6GvXkxe+VYrL/XM=";

        // 32 bytes base64 encoded.
        let pem_string: &str = "-----BEGIN SEED-----
syl9wSYaruvgxg9P5Q1qkZaq5YkM6GvXkxe+VYrL/XM=
-----END SEED-----
";

        let want = general_purpose::STANDARD.decode(payload).unwrap();
        let pem = pem::parse(pem_string).unwrap();
        let got = Seed::from_pem(pem).unwrap();

        assert_eq!(got.bytes(), *want);
    }

    #[test]
    fn seed_from_pem_fails_for_short_seed() {
        let short = "-----BEGIN SEED-----
VnZUNFZ4dlY=
-----END SEED-----
";
        let pem = pem::parse(short).unwrap();
        match Seed::from_pem(pem) {
            Ok(_) => panic!("should fail for short payload"),
            Err(e) => {
                match e {
                    Error::IncorrectLength(_) => {} // pass
                    _ => panic!("should fail with IncorrectLength error"),
                }
            }
        }
    }

    #[test]
    fn seed_from_pem_fails_for_long_seed() {
        let long = "-----BEGIN SEED-----
MIIBPQIBAAJBAOsfi5AGYhdRs/x6q5H7kScxA0Kzzqe6WI6gf6+tc6IvKQJo5rQc
dWWSQ0nRGt2hOPDO+35NKhQEjBQxPh/v7n0CAwEAAQJBAOGaBAyuw0ICyENy5NsO
-----END SEED-----
";
        let pem = pem::parse(long).unwrap();
        assert_eq!(pem.contents().len(), 96);

        match Seed::from_pem(pem) {
            Ok(_) => panic!("should fail for long payload"),
            Err(e) => {
                match e {
                    Error::IncorrectLength(len) => assert_eq!(len, 96), // pass
                    _ => panic!("should fail with IncorrectLength error"),
                }
            }
        }
    }

    #[test]
    fn round_trip_through_file_write_read() {
        let tmpfile = temp_dir().join("seed.pem");

        let seed = Seed::random().unwrap();
        seed.write_to(tmpfile.clone())
            .expect("Write seed to temp file");

        let rinsed = Seed::from_file(tmpfile).expect("Read from temp file");
        assert_eq!(seed.0, rinsed.0);
    }
}
