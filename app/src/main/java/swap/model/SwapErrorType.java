package swap.model;

public enum SwapErrorType {
    SellerInsufficientBalance,
    SwapPunished,
    FailedToInitBitcoinWallet,
    FailedToInitRefundBitcoinWallet,
    FailedToInitMoneroWallet,
    EventLoopPanic,
    DeserializeCborError,
    EncryptedSignatureTransferError,
    EncryptedSignatureAckError,
    SellerEncounteredProblemError,
    FailedToInitAsbMoneroWallet,
    FailedToInitAsbBitcoinWallet,
    FailedToLoadSwapDatabase,
    FailedToLoadAsbDatabase,
    SwapDisconnected,
    FailedToReceiveTransferProof,
    ElectrumMissingTransaction,
    ElectrumFailedToSubscribeToHeaders,

    UnknownError;

    public boolean shouldRestart() {
        return this == FailedToInitMoneroWallet || this == FailedToInitBitcoinWallet || this == DeserializeCborError || this == EncryptedSignatureTransferError || this == SellerEncounteredProblemError || this == EncryptedSignatureAckError || this == FailedToReceiveTransferProof || this == ElectrumMissingTransaction || this == ElectrumFailedToSubscribeToHeaders;
    }
}
