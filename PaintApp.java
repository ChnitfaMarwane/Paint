import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class PaintApp extends JFrame {

    public enum Tool {
        PENCIL("Pencil"), LINE("Line"), RECTANGLE("Rectangle"), OVAL("Oval"), ERASER("Eraser"), FILL("Fill (Bucket)");
        private final String label;
        Tool(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private final CanvasPanel canvas = new CanvasPanel();
    private final JLabel colorIndicator = new JLabel();
    private final JLabel statusLabel = new JLabel(" Ready");
    
    // Reusable actions for global shortcuts and context menus
    private Action undoAction;
    private Action copyAction;
    private Action pasteAction;
    private Action aboutAction;

    public PaintApp() {
        super("Java Paint - Advanced");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        setupActions();

        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
        northPanel.add(createToolsToolBar());
        northPanel.add(createColorsToolBar());
        add(northPanel, BorderLayout.NORTH);

        add(new JScrollPane(canvas), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
        canvas.setStatusLabel(statusLabel);

        setJMenuBar(createMenuBar());
        canvas.setComponentPopupMenu(createContextMenu());

        updateColorIndicator();
        setSize(1050, 750);
        setLocationRelativeTo(null);
    }
    
    private void setupActions() {
        undoAction = new AbstractAction("Undo") {
            public void actionPerformed(ActionEvent e) { canvas.undo(); }
        };
        undoAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));

        copyAction = new AbstractAction("Copy") {
            public void actionPerformed(ActionEvent e) { canvas.copyToClipboard(); }
        };
        copyAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));

        pasteAction = new AbstractAction("Paste") {
            public void actionPerformed(ActionEvent e) { canvas.pasteFromClipboard(); }
        };
        pasteAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK));

        aboutAction = new AbstractAction("About") {
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(PaintApp.this, 
                    "Java Paint App\nDemonstrates Java 2D API, System Clipboard, Undo Stack, and BFS Flood Fill.", 
                    "About", JOptionPane.INFORMATION_MESSAGE);
            }
        };
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        JMenuItem openItem = new JMenuItem("Open...");
        JMenuItem saveItem = new JMenuItem("Save as PNG...");
        JMenuItem clearItem = new JMenuItem("Clear Canvas");
        JMenuItem exitItem = new JMenuItem("Exit");

        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));

        openItem.addActionListener(e -> openImage());
        saveItem.addActionListener(e -> saveImage());
        clearItem.addActionListener(e -> clearCanvas());
        exitItem.addActionListener(e -> exitApplication());

        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.addSeparator();
        fileMenu.add(clearItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        JMenu editMenu = new JMenu("Edit");
        editMenu.setMnemonic(KeyEvent.VK_E);
        editMenu.add(new JMenuItem(undoAction));
        editMenu.addSeparator();
        editMenu.add(new JMenuItem(copyAction));
        editMenu.add(new JMenuItem(pasteAction));
        
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);
        helpMenu.add(new JMenuItem(aboutAction));

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(helpMenu);
        return menuBar;
    }

    private JPopupMenu createContextMenu() {
        JPopupMenu popup = new JPopupMenu();
        popup.add(new JMenuItem(undoAction));
        popup.addSeparator();
        popup.add(new JMenuItem(copyAction));
        popup.add(new JMenuItem(pasteAction));
        popup.addSeparator();
        popup.add(new JMenuItem(aboutAction));
        return popup;
    }

    private JToolBar createToolsToolBar() {
        JToolBar toolBar = new JToolBar("Tools");
        toolBar.setFloatable(false);
        toolBar.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 3));
        toolBar.add(new JLabel("Tool: "));

        ButtonGroup group = new ButtonGroup();
        for (Tool tool : Tool.values()) {
            JToggleButton button = new JToggleButton(tool.getLabel());
            button.setSelected(tool == Tool.PENCIL);
            button.addActionListener(e -> {
                canvas.setTool(tool);
                statusLabel.setText(" Tool: " + tool.getLabel());
            });
            group.add(button);
            toolBar.add(button);
        }

        toolBar.addSeparator();
        toolBar.add(new JLabel("Brush size: "));
        Integer[] sizes = {1, 2, 4, 6, 8, 12, 16, 24};
        JComboBox<Integer> sizeBox = new JComboBox<>(sizes);
        sizeBox.setSelectedItem(2);
        sizeBox.addActionListener(e -> canvas.setBrushSize((Integer) sizeBox.getSelectedItem()));
        toolBar.add(sizeBox);

        toolBar.addSeparator();
        JCheckBox fillBox = new JCheckBox("Fill shapes");
        fillBox.addActionListener(e -> canvas.setFillShapes(fillBox.isSelected()));
        toolBar.add(fillBox);

        return toolBar;
    }

    private JToolBar createColorsToolBar() {
        JToolBar toolBar = new JToolBar("Colors");
        toolBar.setFloatable(false);
        toolBar.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 3));
        toolBar.add(new JLabel("Colors: "));

        Color[] colors = {Color.BLACK, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.ORANGE, Color.MAGENTA};
        for (Color c : colors) {
            JButton button = new JButton();
            button.setBackground(c);
            button.setOpaque(true);
            button.setBorder(new LineBorder(Color.DARK_GRAY));
            button.setPreferredSize(new Dimension(28, 28));
            button.addActionListener(e -> {
                canvas.setColor(c);
                updateColorIndicator();
            });
            toolBar.add(button);
        }

        JButton customButton = new JButton("Custom Color...");
        customButton.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, "Choose a Brush Color", canvas.getColor());
            if (chosen != null) { 
                canvas.setColor(chosen);
                updateColorIndicator();
            }
        });
        toolBar.add(customButton);

        toolBar.addSeparator();
        toolBar.add(new JLabel("Current: "));
        colorIndicator.setOpaque(true);
        colorIndicator.setPreferredSize(new Dimension(40, 24));
        colorIndicator.setBorder(new LineBorder(Color.DARK_GRAY));
        toolBar.add(colorIndicator);

        return toolBar;
    }

    private void updateColorIndicator() { colorIndicator.setBackground(canvas.getColor()); }

    private void clearCanvas() {
        int answer = JOptionPane.showConfirmDialog(this, "Are you sure you want to clear the canvas?",
                "Clear Canvas", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            canvas.clear();
            statusLabel.setText(" Canvas cleared");
        }
    }

    private void openImage() {
        if (canvas.isModified()) {
            int answer = JOptionPane.showConfirmDialog(this, "Opening an image will replace your current drawing.\nContinue?",
                    "Open Image", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "gif", "bmp"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                BufferedImage loaded = ImageIO.read(chooser.getSelectedFile());
                if (loaded == null) {
                    JOptionPane.showMessageDialog(this, "Invalid image.", "Error", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                canvas.setImage(loaded);
                statusLabel.setText(" Opened: " + chooser.getSelectedFile().getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error reading file:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PNG Images (*.png)", "png"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".png")) file = new File(file.getAbsolutePath() + ".png");
            if (file.exists()) {
                int answer = JOptionPane.showConfirmDialog(this, "File exists. Overwrite?", "Confirm", JOptionPane.YES_NO_OPTION);
                if (answer != JOptionPane.YES_OPTION) return;
            }
            try {
                ImageIO.write(canvas.getImage(), "png", file);
                canvas.setModified(false);
                statusLabel.setText(" Saved: " + file.getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error saving file:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exitApplication() {
        if (canvas.isModified()) {
            int answer = JOptionPane.showConfirmDialog(this, "Unsaved changes. Exit anyway?", "Exit", JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) return;
        }
        System.exit(0);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PaintApp().setVisible(true));
    }
}