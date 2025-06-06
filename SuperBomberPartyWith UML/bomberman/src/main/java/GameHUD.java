import gameobjects.Bomber;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class GameHUD {

    private Bomber[] players;
    private BufferedImage[] playerInfo;
    private int[] playerScore;
    boolean matchSet;

    GameHUD() {
        this.players = new Bomber[4];
        this.playerInfo = new BufferedImage[4];
        this.playerScore = new int[4];
        this.matchSet = false;
    }

    void init() {
        // Height of the HUD
        int height = GameWindow.HUD_HEIGHT;
        int infoWidth = GamePanel.panelWidth / 4;

        this.playerInfo[0] = new BufferedImage(infoWidth, height, BufferedImage.TYPE_INT_RGB);
        this.playerInfo[1] = new BufferedImage(infoWidth, height, BufferedImage.TYPE_INT_RGB);
        this.playerInfo[2] = new BufferedImage(infoWidth, height, BufferedImage.TYPE_INT_RGB);
        this.playerInfo[3] = new BufferedImage(infoWidth, height, BufferedImage.TYPE_INT_RGB);
    }

    /**
     * Used by game panel to draw player info to the screen
     * @return Player info box
     */
    BufferedImage getP1info() {
        return this.playerInfo[0];
    }
    BufferedImage getP2info() {
        return this.playerInfo[1];
    }
    BufferedImage getP3info() {
        return this.playerInfo[2];
    }
    BufferedImage getP4info() {
        return this.playerInfo[3];
    }

    /**
     * Assign an info box to a player that shows the information on this player.
     * @param player The player to be assigned
     * @param playerID Used as an index for the array
     */
    void assignPlayer(Bomber player, int playerID) {
        this.players[playerID] = player;
    }

    /*
      Checks if there is only one player alive left and increases their score.
     */
    public void updateScore() throws UnsupportedAudioFileException, IOException {
        // Count dead players
        int deadPlayers = 0;

        for (int i = 0; i < this.players.length; i++) {
            Bomber player = this.players[i];
            if (player == null) continue; // Safety check

            if (player.isDead()) {
                deadPlayers++;
            } else if (player.isNPC()) {
                // Player is alive and is an NPC --> Level Up!
                player.levelUp();
            }
        }

        // Check for the last player standing and conclude the match
        if (deadPlayers == this.players.length - 1) {
            for (int i = 0; i < this.players.length; i++) {
                if (!this.players[i].isDead()) {
                    this.playerScore[i]++;
                    this.matchSet = true;
                }
            }
        } else if (deadPlayers >= this.players.length) {
            // This should only happen if everybody dies at the same time
            this.matchSet = true;
        }
    }


    /**
     * Continuously redraw player information such as score.
     */
    void drawHUD() {
        Graphics[] playerGraphics = {
                this.playerInfo[0].createGraphics(),
                this.playerInfo[1].createGraphics(),
                this.playerInfo[2].createGraphics(),
                this.playerInfo[3].createGraphics()};

        // Clean info boxes
        playerGraphics[0].clearRect(0, 0, playerInfo[0].getWidth(), playerInfo[0].getHeight());
        playerGraphics[1].clearRect(0, 0, playerInfo[1].getWidth(), playerInfo[1].getHeight());
        playerGraphics[2].clearRect(0, 0, playerInfo[1].getWidth(), playerInfo[1].getHeight());
        playerGraphics[3].clearRect(0, 0, playerInfo[1].getWidth(), playerInfo[1].getHeight());

        // Set border color per player
        playerGraphics[0].setColor(Color.WHITE);    // Player 1 info box border color
        playerGraphics[1].setColor(Color.GRAY);     // Player 2 info box border color
        playerGraphics[2].setColor(Color.RED);      // Player 3 info box border color
        playerGraphics[3].setColor(Color.BLUE);     // Player 4 info box border color

        // Iterate loop for each player
        for (int i = 0; i < playerGraphics.length; i++) {
            Font font = new Font("Courier New", Font.BOLD, 24);
            // Draw border and sprite
            playerGraphics[i].drawRect(1, 1, this.playerInfo[i].getWidth() - 2, this.playerInfo[i].getHeight() - 2);
            playerGraphics[i].drawImage(this.players[i].getBaseSprite(), 0, 0, null);

            // Draw score
            playerGraphics[i].setFont(font);
            playerGraphics[i].setColor(Color.WHITE);
            playerGraphics[i].drawString("" + this.playerScore[i], this.playerInfo[i].getWidth() / 2, 32);


            playerGraphics[i].dispose();
        }
    }
    public Bomber getPlayer(int playerIndex) {
        if (playerIndex >= 0 && playerIndex < players.length) {
            return players[playerIndex];
        }
        return null;
    }
}
