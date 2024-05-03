package swap.client;

import com.samourai.whirlpool.client.wallet.beans.SamouraiAccountIndex;
import org.berndpruenster.netlayer.tor.Tor;
import org.bitcoinj.core.NetworkParameters;
import org.json.JSONObject;
import swap.helper.HelperAddress;
import swap.helper.HelperRawJsonDb;
import swap.helper.HelperSwapsDb;
import swap.lib.App;
import swap.lib.AppSwap;
import swap.lib.AppXmrRpc;
import swap.model.request.CancelAndRefundRequest;
import swap.model.request.ResumeRequest;
import swap.model.request.SwapRequest;
import swap.process.ProcessRpcMonero;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientSwap implements App {
    private final AppSwap appSwap;
    private ProcessRpcMonero xmrRpcProcess = null;
    @Nullable
    private SwapRequest swapRequest = null;
    @Nullable
    private ResumeRequest resumeRequest = null;

    public AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread = null;

    public ClientSwap(AppSwap appSwap, SwapRequest swapRequest) {
        this.appSwap = appSwap;
        startXmrRpcProcess(swapRequest.params(), swapRequest.proxyPort());
        this.swapRequest = new SwapRequest(
                swapRequest.uuid(),
                swapRequest.seedBase64(),
                swapRequest.xmrReceiveAddress(),
                swapRequest.electrumUrl(),
                swapRequest.libp2pPeerAddress(),
                swapRequest.proxy(),
                this.xmrRpcProcess.endpoint(),
                swapRequest.params(),
                swapRequest.proxyPort(),
                swapRequest.refundAddress(),
                swapRequest.swapsAccount()
        );
    }

    public ClientSwap(AppSwap appSwap, ResumeRequest resumeRequest) {
        this.appSwap = appSwap;
        startXmrRpcProcess(resumeRequest.params(), resumeRequest.proxyPort());
        long swapsAccount = HelperSwapsDb.getInstance().getSwapsAccount(resumeRequest.swapId());
        this.resumeRequest = new ResumeRequest(
                resumeRequest.seedBase64(),
                resumeRequest.swapId(),
                resumeRequest.electrumUrl(),
                resumeRequest.proxy(),
                this.xmrRpcProcess.endpoint(),
                resumeRequest.params(),
                resumeRequest.proxyPort(),
                swapsAccount
        );
    }

    @Override
    public void start() {
        running.set(true);

        thread = new Thread(() -> {
            while (running.get()) {
                if (swapRequest != null) {
                    buyXmr();
                } else if (resumeRequest != null) {
                    resume();
                }
            }
        });
        thread.start();
    }

    @Override
    public void stop() {
        running.set(false);
        stopXmrRpcProcess();
        thread = null;
    }

    @Override
    public void restart() {
        stop();
        start();
    }

    public void restartXmrRpcProcess() {
        stopXmrRpcProcess();
        startXmrRpcProcess(getParams(), getProxyPort());
    }

    private void stopXmrRpcProcess() {
        ProcessRpcMonero processRpcMonero = getXmrRpcProcess();
        if (processRpcMonero == null) return;
        boolean success = processRpcMonero.shutdown();
        if (success) xmrRpcProcess = null;
    }

    private void startXmrRpcProcess(NetworkParameters parameters, int proxyPort) {
        try {
            if (Tor.getDefault() != null) {
                this.xmrRpcProcess = AppXmrRpc.startMoneroRpc(parameters, appSwap.getRpcRootDir().getCanonicalPath(), appSwap.getMoneroDaemon(), proxyPort, AppXmrRpc.RPC_SWAPCLIENT);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ProcessRpcMonero getXmrRpcProcess() {
        return xmrRpcProcess;
    }

    private void buyXmr() {
        if (swapRequest == null) return;
        if (HelperAddress.isLibp2pPeerValid(swapRequest.libp2pPeerAddress()) && HelperAddress.isXmrAddrValid(swapRequest.xmrReceiveAddress())) {
            buyXmr(swapRequest.toJson().toString());
        }
    }

    private void resume() {
        if (resumeRequest == null) return;
        String swapId = resumeRequest.swapId();
        if (isValidUuid(swapId)) {
            resume(resumeRequest.toJson().toString());
        }
    }

    public String cancelAndRefund(CancelAndRefundRequest cancelAndRefundRequest) {
        return cancelAndRefund(cancelAndRefundRequest.toJson().toString());
    }

    private native void buyXmr(String json);

    private native void resume(String json);

    private native String cancelAndRefund(String json);

    private boolean isValidUuid(String uuid) {
        try {
            UUID.fromString(uuid);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private NetworkParameters getParams() {
        return swapRequest != null ? swapRequest.params() : resumeRequest != null ? resumeRequest.params() : null;
    }

    private int getProxyPort() {
        return swapRequest != null ? swapRequest.proxyPort() : resumeRequest != null ? resumeRequest.proxyPort() : -1;
    }
}
