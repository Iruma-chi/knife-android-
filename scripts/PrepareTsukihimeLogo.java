import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

/** Builds a square adaptive-icon foreground using the official wide Tsukihime wordmark. */
public final class PrepareTsukihimeLogo {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: input-logo.png output-icon.png");
        BufferedImage source = ImageIO.read(new File(args[0]));
        if (source == null) throw new IllegalArgumentException("Official logo PNG could not be decoded");
        int size = 432;
        BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        try {
            g.setColor(new Color(0x07, 0x11, 0x27));
            g.fillRect(0, 0, size, size);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            double scale = Math.min(390.0 / source.getWidth(), 250.0 / source.getHeight());
            int width = (int)Math.round(source.getWidth() * scale);
            int height = (int)Math.round(source.getHeight() * scale);
            int x = (size - width) / 2;
            int y = (size - height) / 2;
            g.drawImage(source, x, y, width, height, null);
        } finally {
            g.dispose();
        }
        ImageIO.write(output, "png", new File(args[1]));
    }
}
