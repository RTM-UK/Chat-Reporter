
package me.RTM.reportplugin;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ScreenshotManager {

    private final JavaPlugin plugin;

    public ScreenshotManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void capturePlayer(Player player, String reportId) {

        try {

            File folder = new File(plugin.getDataFolder(), "screenshots");

            if (!folder.exists()) {
                folder.mkdirs();
            }

            BufferedImage image = new BufferedImage(600, 200, BufferedImage.TYPE_INT_RGB);

            Graphics2D g = image.createGraphics();

            g.setColor(Color.BLACK);
            g.fillRect(0, 0, 600, 200);

            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 24));

            g.drawString("Player Report Screenshot", 20, 50);
            g.drawString("Player: " + player.getName(), 20, 100);
            g.drawString("UUID: " + player.getUniqueId(), 20, 140);

            g.dispose();

            File output = new File(folder, reportId + ".png");

            ImageIO.write(image, "png", output);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
