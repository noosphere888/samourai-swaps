package swap.gui.image;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

public class AddressQrCode extends WritableImage {
    private static final int widthAndHeight = 300;
    private final String address;

    public AddressQrCode(String address) {
        super(widthAndHeight, widthAndHeight);
        this.address = address;
    }

    public Image generateBtc() throws Exception {
        return generate("bitcoin:" + address);
    }

    public Image generateXmr() throws Exception {
        return generate("monero:" + address);
    }

    public Image generate(String address) throws Exception {
        BitMatrix matrix = new MultiFormatWriter().encode(address, BarcodeFormat.QR_CODE, widthAndHeight, widthAndHeight);
        PixelWriter pw = this.getPixelWriter();
        for (int x = 0; x < matrix.getWidth(); x++) {
            for (int y = 0; y < matrix.getHeight(); y++) {
                if (matrix.get(x, y)) {
                    pw.setColor(x, y, Color.BLACK);
                } else {
                    pw.setColor(x, y, Color.WHITE);
                }
            }
        }
        return new ImageView(this).getImage();
    }
}
