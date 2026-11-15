import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;

public class CanvasPanel extends JPanel {
    private static final int DEFAULT_WIDTH = 900;
    private static final int DEFAULT_HEIGHT = 600;

    private BufferedImage image;
    private final Stack<BufferedImage> undoStack = new Stack<>();
    
    private PaintApp.Tool tool = PaintApp.Tool.PENCIL;
    private Color color = Color.BLACK;
    private float brushSize = 2f;
    private boolean fillShapes = false;
    private boolean modified = false;

    private Point startPoint;
    private Shape previewShape;
    private GeneralPath freehandPath;
    private JLabel statusLabel;

    public CanvasPanel() {
        createBlankImage(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // Suppress drawing triggers on context menu click
                if (SwingUtilities.isRightMouseButton(e)) return;
                
                saveStateForUndo(); 
                startPoint = e.getPoint();

                if (tool == PaintApp.Tool.FILL) {
                    floodFill(startPoint, color);
                    modified = true;
                    repaint();
                    return;
                }

                if (isFreehandTool()) {
                    freehandPath = new GeneralPath();
                    freehandPath.moveTo(e.getX(), e.getY());
                    freehandPath.lineTo(e.getX(), e.getY());
                    previewShape = freehandPath;
                } else {
                    previewShape = null;
                }
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e) || tool == PaintApp.Tool.FILL) return;
                updatePreview(e.getPoint());
                updateStatus(e.getPoint());
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e) || startPoint == null || tool == PaintApp.Tool.FILL) return;
                updatePreview(e.getPoint());
                commitShape();
                startPoint = null;
                previewShape = null;
                freehandPath = null;
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) { updateStatus(e.getPoint()); }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    private boolean isFreehandTool() { return tool == PaintApp.Tool.PENCIL || tool == PaintApp.Tool.ERASER; }

    private void updatePreview(Point current) {
        if (startPoint == null) return;
        double x = Math.min(startPoint.x, current.x);
        double y = Math.min(startPoint.y, current.y);
        double w = Math.abs(current.x - startPoint.x);
        double h = Math.abs(current.y - startPoint.y);

        switch (tool) {
            case PENCIL:
            case ERASER:
                freehandPath.lineTo(current.x, current.y);
                previewShape = freehandPath;
                break;
            case LINE:
                previewShape = new Line2D.Double(startPoint, current);
                break;
            case RECTANGLE:
                previewShape = new Rectangle2D.Double(x, y, w, h);
                break;
            case OVAL:
                previewShape = new Ellipse2D.Double(x, y, w, h);
                break;
            default: break;
        }
    }

    private void commitShape() {
        if (previewShape == null) return;
        Graphics2D g2 = image.createGraphics();
        render(g2, previewShape);
        g2.dispose();
        modified = true;
    }

    private void render(Graphics2D g2, Shape shape) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        boolean erasing = (tool == PaintApp.Tool.ERASER);
        g2.setColor(erasing ? Color.WHITE : color);
        float width = erasing ? brushSize * 3 : brushSize;
        g2.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        boolean closedShape = (tool == PaintApp.Tool.RECTANGLE || tool == PaintApp.Tool.OVAL);
        if (closedShape && fillShapes) g2.fill(shape);
        else g2.draw(shape);
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.drawImage(image, 0, 0, null);
        if (previewShape != null) render(g2, previewShape);
    }

    // BFS implementation for continuous color replacement
    private void floodFill(Point pt, Color replacementColor) {
        if (pt.x < 0 || pt.x >= image.getWidth() || pt.y < 0 || pt.y >= image.getHeight()) return;
        int targetRGB = image.getRGB(pt.x, pt.y);
        int replacementRGB = replacementColor.getRGB();
        if (targetRGB == replacementRGB) return;

        Queue<Point> q = new LinkedList<>();
        q.add(pt);
        while (!q.isEmpty()) {
            Point p = q.remove();
            if (p.x < 0 || p.x >= image.getWidth() || p.y < 0 || p.y >= image.getHeight()) continue;
            if (image.getRGB(p.x, p.y) == targetRGB) {
                image.setRGB(p.x, p.y, replacementRGB);
                q.add(new Point(p.x - 1, p.y));
                q.add(new Point(p.x + 1, p.y));
                q.add(new Point(p.x, p.y - 1));
                q.add(new Point(p.x, p.y + 1));
            }
        }
    }

    // State management for undo operations
    private void saveStateForUndo() {
        if (undoStack.size() > 20) undoStack.remove(0); // Cap stack depth to prevent OutOfMemoryError
        undoStack.push(deepCopyImage(image));
    }

    public void undo() {
        if (!undoStack.isEmpty()) {
            image = undoStack.pop();
            repaint();
        }
    }

    private BufferedImage deepCopyImage(BufferedImage bi) {
        ColorModel cm = bi.getColorModel();
        WritableRaster raster = bi.copyData(null);
        return new BufferedImage(cm, raster, cm.isAlphaPremultiplied(), null);
    }

    // OS clipboard integration
    public void copyToClipboard() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new TransferableImage(image), null);
    }

    public void pasteFromClipboard() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        try {
            if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                saveStateForUndo();
                Image pasted = (Image) clipboard.getData(DataFlavor.imageFlavor);
                Graphics2D g2 = image.createGraphics();
                g2.drawImage(pasted, 0, 0, null);
                g2.dispose();
                modified = true;
                repaint();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Wrapper for BufferedImage clipboard transfer
    private static class TransferableImage implements Transferable {
        private final Image img;
        public TransferableImage(Image img) { this.img = img; }
        public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.imageFlavor}; }
        public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.imageFlavor.equals(flavor); }
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (isDataFlavorSupported(flavor)) return img;
            throw new UnsupportedFlavorException(flavor);
        }
    }

    private void createBlankImage(int width, int height) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, width, height);
        g2.dispose();
        setPreferredSize(new Dimension(width, height));
        revalidate();
    }

    public void clear() {
        saveStateForUndo();
        createBlankImage(image.getWidth(), image.getHeight());
        modified = false;
        repaint();
    }

    public void setImage(BufferedImage loaded) {
        saveStateForUndo();
        BufferedImage copy = new BufferedImage(loaded.getWidth(), loaded.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = copy.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, copy.getWidth(), copy.getHeight());
        g2.drawImage(loaded, 0, 0, null);
        g2.dispose();
        image = copy;
        setPreferredSize(new Dimension(copy.getWidth(), copy.getHeight()));
        revalidate();
        modified = false;
        repaint();
    }

    public BufferedImage getImage() { return image; }
    public void setStatusLabel(JLabel label) { this.statusLabel = label; }
    private void updateStatus(Point p) {
        if (statusLabel != null) statusLabel.setText(" Tool: " + tool.getLabel() + "   |   x=" + p.x + ", y=" + p.y);
    }

    public void setTool(PaintApp.Tool tool)   { this.tool = tool; }
    public void setColor(Color color)         { this.color = color; }
    public Color getColor()                   { return color; }
    public void setBrushSize(float size)      { this.brushSize = size; }
    public void setFillShapes(boolean fill)   { this.fillShapes = fill; }
    public boolean isModified()               { return modified; }
    public void setModified(boolean modified) { this.modified = modified; }
}