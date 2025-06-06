import com.sun.media.jfxmedia.MediaManager;
import gameobjects.Bomber;
import gameobjects.GameObject;
import gameobjects.Powerup;
import gameobjects.Wall;
import util.GameObjectCollection;
import util.Key;
import util.ResourceCollection;

import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.*;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;


public class GamePanel extends JPanel implements Runnable {
    int background;
    Audio audio;
    private int floorPulseTimer = 0;
    private final int FLOOR_PULSE_DURATION = 30; // Frames to stay bright (you can tweak)
    private boolean floorPulsing = false;

    // Screen size is determined by the map size
    static int panelWidth;
    static int panelHeight;

    private Thread thread;
    private boolean running;
    int resetDelay;

    private BufferedImage world;
    private Graphics2D buffer;
    private BufferedImage bg;
    public GameHUD gameHUD;
    private int mapWidth;
    private int mapHeight;
    private ArrayList<ArrayList<String>> mapLayout;
    private BufferedReader bufferedReader;

    private HashMap<Integer, Key> controls1;
    private HashMap<Integer, Key> controls2;
    private HashMap<Integer, Key> controls3;
    private HashMap<Integer, Key> controls4;

    private static final double SOFTWALL_RATE = 0.825;
    private boolean hud;

    /**
     * Construct game panel and load in a map file.
     *
     * @param filename Name of the map file
     */
    GamePanel(String filename) {
        background = 1;
        audio = new Audio(0);
        this.setFocusable(true);
        this.requestFocus();
        this.setControls();
        this.bg = ResourceCollection.Images.BACKGROUND.getImage();
        this.loadMapFile(filename);
        this.addKeyListener(new GameController(this));
    }
    // New constructor for random map loading
    public GamePanel(BufferedReader reader) {
        background = 1;
        audio = new Audio(0);
        this.setFocusable(true);
        this.requestFocus();
        this.setControls();
        this.bg = ResourceCollection.Images.BACKGROUND.getImage();
        this.bufferedReader = reader;
        this.loadMapData();
        this.addKeyListener(new GameController(this));
    }

    // Parses the CSV data into mapLayout
    private void loadMapData() {
        this.mapLayout = new ArrayList<>();
        try {
            String currentLine;
            while ((currentLine = bufferedReader.readLine()) != null) {
                if (currentLine.isEmpty()) continue;
                mapLayout.add(new ArrayList<>(Arrays.asList(currentLine.split(","))));
            }
        } catch (IOException e) {
            System.out.println(e + ": Error parsing map data");
            e.printStackTrace();
        }
    }

    /**
     * Initialize the game panel with a HUD, window size, collection of game objects, and start the game loop.
     */
    void init() {
        this.resetDelay = 0;
        GameObjectCollection.init();
        this.gameHUD = new GameHUD();
        this.generateMap();
        // After parsing map data, validate column count
        int expectedCols = mapLayout.get(0).size();
        for (int i = 0; i < mapLayout.size(); i++) {
            if (mapLayout.get(i).size() != expectedCols) {
                System.err.println("❌ Row " + i + " has mismatched columns: " + mapLayout.get(i).size() + " (expected " + expectedCols + ")");
            }
        }

        this.gameHUD.init();
        this.setPreferredSize(new Dimension(this.mapWidth * 32, (this.mapHeight * 32)
                + GameWindow.HUD_HEIGHT));
        System.gc();
        this.running = true;
    }

    private void loadMapFile(String mapFile) {
        // Loading map file
        background = 1;
        audio = new Audio(0);
        try {
            this.bufferedReader = new BufferedReader(new FileReader(mapFile));
        } catch (IOException | NullPointerException e) {
            // Load default map when map file could not be loaded
            System.err.println(e + ": Cannot load map file, loading default map");
            this.bufferedReader = new BufferedReader(ResourceCollection.Files.DEFAULT_MAP.getFile());
        }

        // Parsing map data from file
        this.mapLayout = new ArrayList<>();
        try {
            String currentLine;
            while ((currentLine = bufferedReader.readLine()) != null) {
                if (currentLine.isEmpty()) {
                    continue;
                }
                // Split row into array of strings and add to array list
                mapLayout.add(new ArrayList<>(Arrays.asList(currentLine.split(","))));
            }
        } catch (IOException | NullPointerException e) {
            System.out.println(e + ": Error parsing map data");
            e.printStackTrace();
        }
    }

    /**
     * Generate the map given the map file. The map is grid based and each tile is 32x32.
     * Create game objects depending on the string.
     */
    private void generateMap() {
        // Map dimensions
        this.mapWidth = mapLayout.get(0).size();
        this.mapHeight = mapLayout.size();
        panelWidth = this.mapWidth * 32;
        panelHeight = this.mapHeight * 32;

        this.world = new BufferedImage(this.mapWidth * 32, this.mapHeight * 32, BufferedImage.TYPE_INT_RGB);

        // Generate entire map
        for (int y = 0; y < this.mapHeight; y++) {
            for (int x = 0; x < this.mapWidth; x++) {
                switch (mapLayout.get(y).get(x)) {
                    case ("S"):     // Soft wall; breakable
                        if (Math.random() < SOFTWALL_RATE) {
                            BufferedImage sprSoftWall = ResourceCollection.Images.SOFT_WALL.getImage();
                            Wall softWall = new Wall(new Point2D.Float(x * 32, y * 32), sprSoftWall, true);
                            GameObjectCollection.spawn(softWall);
                        }
                        break;

                    case ("H"):     // Hard wall; unbreakable
                        int key = 0;
                        if (y > 0 && mapLayout.get(y - 1).get(x).equals("H")) key |= 0b0001; // North
                        if (x < mapWidth - 1 && mapLayout.get(y).get(x + 1).equals("H")) key |= 0b0010; // East
                        if (y < mapHeight - 1 && mapLayout.get(y + 1).get(x).equals("H")) key |= 0b0100; // South
                        if (x > 0 && mapLayout.get(y).get(x - 1).equals("H")) key |= 0b1000; // West

                        BufferedImage sprHardWall = ResourceCollection.getHardWallTile(key);
                        Wall hardWall = new Wall(new Point2D.Float(x * 32, y * 32), sprHardWall, false);
                        GameObjectCollection.spawn(hardWall);
                        break;

                    case ("1"):     // Player 1; Bomber
                        BufferedImage[][] sprMapP1 = ResourceCollection.SpriteMaps.PLAYER_1.getSprites();
                        Bomber player1 = new Bomber(alignBomber(x, y, sprMapP1), sprMapP1);
                        PlayerController playerController1 = new PlayerController(player1, this.controls1);
                        this.addKeyListener(playerController1);
                        this.gameHUD.assignPlayer(player1, 0);
                        GameObjectCollection.spawn(player1);
                        break;

                    case ("2"):     // Player 2; Bomber
                        BufferedImage[][] sprMapP2 = ResourceCollection.SpriteMaps.PLAYER_2.getSprites();
                        Bomber player2 = new Bomber(alignBomber(x, y, sprMapP2), sprMapP2);
                        PlayerController playerController2 = new PlayerController(player2, this.controls2);
                        this.addKeyListener(playerController2);
                        this.gameHUD.assignPlayer(player2, 1);
                        GameObjectCollection.spawn(player2);
                        break;

                    case ("3"):     // Player 3; Bomber
                        BufferedImage[][] sprMapP3 = ResourceCollection.SpriteMaps.PLAYER_3.getSprites();
                        Bomber player3 = new Bomber(alignBomber(x, y, sprMapP3), sprMapP3);
                        PlayerController playerController3 = new PlayerController(player3, this.controls3);
                        this.addKeyListener(playerController3);
                        this.gameHUD.assignPlayer(player3, 2);
                        GameObjectCollection.spawn(player3);
                        break;

                    case ("4"):     // Player 4; Bomber
                        BufferedImage[][] sprMapP4 = ResourceCollection.SpriteMaps.PLAYER_4.getSprites();
                        Bomber player4 = new Bomber(alignBomber(x, y, sprMapP4), sprMapP4);
                        PlayerController playerController4 = new PlayerController(player4, this.controls4);
                        this.addKeyListener(playerController4);
                        this.gameHUD.assignPlayer(player4, 3);
                        GameObjectCollection.spawn(player4);
                        break;

                    case ("PB"):    // Powerup Bomb
                        Powerup powerBomb = new Powerup(new Point2D.Float(x * 32, y * 32), Powerup.Type.Bomb);
                        GameObjectCollection.spawn(powerBomb);
                        break;

                    case ("PU"):    // Powerup Fire up
                        Powerup powerFireup = new Powerup(new Point2D.Float(x * 32, y * 32), Powerup.Type.Fireup);
                        GameObjectCollection.spawn(powerFireup);
                        break;

                    case ("PM"):    // Powerup Firemax
                        Powerup powerFiremax = new Powerup(new Point2D.Float(x * 32, y * 32), Powerup.Type.Firemax);
                        GameObjectCollection.spawn(powerFiremax);
                        break;

                    case ("PS"):    // Powerup Speed
                        Powerup powerSpeed = new Powerup(new Point2D.Float(x * 32, y * 32), Powerup.Type.Speed);
                        GameObjectCollection.spawn(powerSpeed);
                        break;

                    case ("PP"):    // Powerup Pierce
                        Powerup powerPierce = new Powerup(new Point2D.Float(x * 32, y * 32), Powerup.Type.Pierce);
                        GameObjectCollection.spawn(powerPierce);
                        break;

                    case ("PK"):    // Powerup Kick
                        Powerup powerKick = new Powerup(new Point2D.Float(x * 32, y * 32), Powerup.Type.Kick);
                        GameObjectCollection.spawn(powerKick);
                        break;

                    case ("PT"):    // Powerup Timer
                        Powerup powerTimer = new Powerup(new Point2D.Float(x * 32, y * 32), Powerup.Type.Timer);
                        GameObjectCollection.spawn(powerTimer);
                        break;

                    default:
                        break;
                }
            }
        }
    }
    private Point2D.Float alignBomber(int x, int y, BufferedImage[][] spriteMap) {
        int frameHeight = spriteMap[0][0].getHeight();
        return new Point2D.Float(x * 32, y * 32 - (frameHeight - 32));


    }

    /**
     * Initialize default key bindings for all players.
     */
    private void setControls() {
        this.controls1 = new HashMap<>();
        this.controls2 = new HashMap<>();
        this.controls3 = new HashMap<>();
        this.controls4 = new HashMap<>();

        // Set Player 1 controls
        this.controls1.put(KeyEvent.VK_UP, Key.up);
        this.controls1.put(KeyEvent.VK_DOWN, Key.down);
        this.controls1.put(KeyEvent.VK_LEFT, Key.left);
        this.controls1.put(KeyEvent.VK_RIGHT, Key.right);
        this.controls1.put(KeyEvent.VK_SLASH, Key.action);

        // Set Player 2 controls
        this.controls2.put(KeyEvent.VK_W, Key.up);
        this.controls2.put(KeyEvent.VK_S, Key.down);
        this.controls2.put(KeyEvent.VK_A, Key.left);
        this.controls2.put(KeyEvent.VK_D, Key.right);
        this.controls2.put(KeyEvent.VK_E, Key.action);

        // Set Player 3 controls
        this.controls3.put(KeyEvent.VK_T, Key.up);
        this.controls3.put(KeyEvent.VK_G, Key.down);
        this.controls3.put(KeyEvent.VK_F, Key.left);
        this.controls3.put(KeyEvent.VK_H, Key.right);
        this.controls3.put(KeyEvent.VK_Y, Key.action);

        // Set Player 4 controls
        this.controls4.put(KeyEvent.VK_I, Key.up);
        this.controls4.put(KeyEvent.VK_K, Key.down);
        this.controls4.put(KeyEvent.VK_J, Key.left);
        this.controls4.put(KeyEvent.VK_L, Key.right);
        this.controls4.put(KeyEvent.VK_O, Key.action);
    }

    /**
     * When ESC is pressed, close the game
     */
    void exit() {
        this.running = false;
    }

    /**
     * When F5 is pressed, reset game object collection, collect garbage, reinitialize game panel, reload map
     */
    void resetGame() {
        this.init();
        {
            background = 1;
            audio = new Audio(0);
        }
    }

    /**
     * Reset only the map, keeping the score
     */
    private void resetMap() {
        Audio.playVictory();
        GameObjectCollection.init();
        this.generateMap();
        System.gc();

    }
    private static GamePanel instance; // 🔥 Add this at the top of the GamePanel class (field)

    @Override
    public void addNotify() {
        super.addNotify();

        if (this.thread == null) {
            instance = this; // 🔥 When GamePanel is created, set instance
            this.thread = new Thread(this, "GameThread");
            this.thread.start();
        }
    }

    // 🔥 Then at the very bottom (outside any method):
    public static void startFloorPulse() {
        if (instance != null) {
            instance.floorPulsing = true;
            instance.floorPulseTimer = instance.FLOOR_PULSE_DURATION;
        }
    }

    /**
     * The game loop.
     * The loop repeatedly calls update and repaints the panel.
     * Also reports the frames drawn per second and updates called per second (ticks).
     */
    @Override
    public void run() {
        long timer = System.currentTimeMillis();
        long lastTime = System.nanoTime();

        final double NS_PER_TICK = 1000000000.0 / 60.0; // 60 ticks per second
        final double NS_PER_FRAME = 1000000000.0 / 144.0; // 144 frames per second

        double deltaTicks = 0;
        double deltaFrames = 0;
        int fps = 0;
        int ticks = 0;

        while (this.running) {
            long now = System.nanoTime();
            deltaTicks += (now - lastTime) / NS_PER_TICK;
            deltaFrames += (now - lastTime) / NS_PER_FRAME;
            lastTime = now;

            if (deltaTicks >= 1) {
                try {
                    this.update();
                } catch (UnsupportedAudioFileException | IOException e) {
                    e.printStackTrace();
                }
                ticks++;
                deltaTicks--;
            }

            if (deltaFrames >= 1) {
                this.repaint();
                fps++;
                deltaFrames--;
            }

            // Update FPS/Ticks counter every second
            if (System.currentTimeMillis() - timer > 1000) {
                timer += 1000;
                GameLauncher.window.update(fps, ticks);
                fps = 0;
                ticks = 0;
            }

            // Sleep a little to prevent CPU maxing out
            try {
                Thread.sleep(1);
            } catch (InterruptedException ignored) {
            }
        }

        System.exit(0);
    }

    /**
     * The update method that loops through every game object and calls update.
     * Checks collisions between every two game objects.
     * Deletes game objects that are marked for deletion.
     * Checks if a player is a winner and updates score, then reset the map.
     */
    private void update() throws UnsupportedAudioFileException, IOException {
        GameObjectCollection.sortBomberObjects();
        // Loop through every game object arraylist
        for (int list = 0; list < GameObjectCollection.gameObjects.size(); list++) {
            for (int objIndex = 0; objIndex < GameObjectCollection.gameObjects.get(list).size(); ) {
                GameObject obj = GameObjectCollection.gameObjects.get(list).get(objIndex);
                obj.update();
                if (obj.isDestroyed()) {
                    // Destroy and remove game objects that were marked for deletion
                    obj.onDestroy();
                    GameObjectCollection.gameObjects.get(list).remove(obj);
                } else {
                    for (int list2 = 0; list2 < GameObjectCollection.gameObjects.size(); list2++) {
                        for (int objIndex2 = 0; objIndex2 < GameObjectCollection.gameObjects.get(list2).size(); objIndex2++) {
                            GameObject collidingObj = GameObjectCollection.gameObjects.get(list2).get(objIndex2);
                            // Skip detecting collision on the same object as itself
                            if (obj == collidingObj) {
                                continue;
                            }

                            // Visitor pattern collision handling
                            if (obj.getCollider().intersects(collidingObj.getCollider())) {
                                // Use one of these
                                collidingObj.onCollisionEnter(obj);
//                                obj.onCollisionEnter(collidingObj);
                            }
                        }
                    }
                    objIndex++;
                }
            }
            if (floorPulsing) {
                floorPulseTimer--;
                if (floorPulseTimer <= 0) {
                    floorPulsing = false;
                }
            }

        }

        // Check for the last bomber to survive longer than the others and increase score
        //  is added immediately so there is no harm of dying when you are the last one
        // Reset map when there are 1 or fewer bombers left
        if (!this.gameHUD.matchSet) {
            this.gameHUD.updateScore();

        } else {
            // Checking size of array list because when a bomber dies, they do not immediately get deleted
            // This makes it so that the next round doesn't start until the winner is the only bomber object on the map
            if (GameObjectCollection.bomberObjects.size() <= 1) {

                //audio needs to play before reset
                //  Audio.playVictory();
                this.resetMap();
                this.gameHUD.matchSet = false;

            }

        }

        // Used to prevent resetting the game really fast
        this.resetDelay++;

        try {
            Thread.sleep(1000 / 144);
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        this.buffer = this.world.createGraphics();
        this.buffer.clearRect(0, 0, this.world.getWidth(), this.world.getHeight());
        super.paintComponent(g2);

        this.gameHUD.drawHUD();

        // Draw background
        BufferedImage backgroundTile = ResourceCollection.Images.BACKGROUND.getImage();
        int tileWidth = backgroundTile.getWidth();
        int tileHeight = backgroundTile.getHeight();

        for (int x = 0; x < getWidth(); x += tileWidth) {
            for (int y = 0; y < getHeight(); y += tileHeight) {
                g2.drawImage(backgroundTile, x, y, null); // ✅ use g2 here
            }
        }

// If pulsing, draw a transparent bright overlay
        if (floorPulsing) {
            Composite oldComposite = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f)); // 20% brightness
            g2.setColor(Color.ORANGE);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setComposite(oldComposite);
        }

        // Draw game objects
        for (int i = 0; i < GameObjectCollection.gameObjects.size(); i++) {
            for (int j = 0; j < GameObjectCollection.gameObjects.get(i).size(); j++) {
                GameObject obj = GameObjectCollection.gameObjects.get(i).get(j);
                obj.drawImage(this.buffer);
//                obj.drawCollider(this.buffer);
            }
        }

        // Draw HUD
        int infoBoxWidth = panelWidth / 4;
        g2.drawImage(this.gameHUD.getP1info(), 0, 0, null);
        g2.drawImage(this.gameHUD.getP2info(), infoBoxWidth, 0, null);
        g2.drawImage(this.gameHUD.getP3info(), infoBoxWidth * 2, 0, null);
        g2.drawImage(this.gameHUD.getP4info(), infoBoxWidth * 3, 0, null);

        // Draw game world offset by the HUD
        g2.drawImage(this.world, 0, GameWindow.HUD_HEIGHT, null);

        g2.dispose();
        this.buffer.dispose();
    }
    public int getMapWidth() {
        return this.mapWidth;
    }

    public int getMapHeight() {
        return this.mapHeight;
    }

    public Bomber spawnPlayer(int playerIndex, Point point) {
        // Convert Point to Float
        Point2D.Float position = new Point2D.Float(point.x, point.y);

        // Load player sprites (make sure SpriteMaps.PLAYERx exists)
        BufferedImage[][] spriteMap;
        switch (playerIndex) {
            case 1:
                spriteMap = ResourceCollection.SpriteMaps.PLAYER1.getSprites();
                break;
            case 2:
                spriteMap = ResourceCollection.SpriteMaps.PLAYER2.getSprites();
                break;
            case 3:
                spriteMap = ResourceCollection.SpriteMaps.PLAYER3.getSprites();
                break;
            case 4:
                spriteMap = ResourceCollection.SpriteMaps.PLAYER4.getSprites();
                break;
            default:
                throw new IllegalArgumentException("Unsupported player index: " + playerIndex);
        }

        // Create the player
        Bomber bomber = new Bomber(position, spriteMap);

        // Add to GameObject list
        GameObjectCollection.spawn(bomber);

        // Optional: register in HUD
        if (this.gameHUD != null) {

            this.gameHUD.assignPlayer(bomber, playerIndex - 1); // Index starts at 0
        }
        return bomber;
    }

    public GameHUD getHUD() {
        return this.gameHUD;
    }
}

/**
 * Used to control the game
 */
class GameController implements KeyListener {

    private GamePanel gamePanel;

    /**
     * Construct a universal game controller key listener for the game.
     *
     * @param gamePanel Attach game controller to this game panel
     */
    GameController(GamePanel gamePanel) {
        this.gamePanel = gamePanel;
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    /**
     * Key events for general game operations such as exit
     *
     * @param e Keyboard key pressed
     */
    @Override
    public void keyPressed(KeyEvent e) {
        // Close game
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            System.out.println("Escape key pressed: Closing game");
            this.gamePanel.exit();
        }

        // Display controls
        if (e.getKeyCode() == KeyEvent.VK_F1) {
            System.out.println("F1 key pressed: Displaying help");

            String[] columnHeaders = {"", "White", "Black", "Red", "Blue"};
            Object[][] controls = {
                    {"Up", "Up", "W", "T", "I"},
                    {"Down", "Down", "S", "G", "K"},
                    {"Left", "Left", "A", "F", "J"},
                    {"Right", "Right", "D", "H", "L"},
                    {"Bomb", "/", "E", "Y", "O"},
                    {"", "", "", "", ""},
                    {"Help", "F1", "", "", ""},
                    {"Reset", "F5", "", "", ""},
                    {"Exit", "ESC", "", "", ""}};

            JTable controlsTable = new JTable(controls, columnHeaders);
            JTableHeader tableHeader = controlsTable.getTableHeader();

            // Wrap JTable inside JPanel to display
            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());
            panel.add(tableHeader, BorderLayout.NORTH);
            panel.add(controlsTable, BorderLayout.CENTER);

            JOptionPane.showMessageDialog(this.gamePanel, panel, "Controls", JOptionPane.PLAIN_MESSAGE);
        }

        // Reset game
        // Delay prevents resetting too fast which causes the game to crash
        if (e.getKeyCode() == KeyEvent.VK_F5) {
            if (this.gamePanel.resetDelay >= 20) {
                System.out.println("F5 key pressed: Resetting game");
                this.gamePanel.resetGame();
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

}
