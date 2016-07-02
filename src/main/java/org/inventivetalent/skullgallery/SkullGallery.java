package org.inventivetalent.skullgallery;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.inventivetalent.pluginannotations.PluginAnnotations;
import org.inventivetalent.pluginannotations.command.Command;
import org.inventivetalent.pluginannotations.command.OptionalArg;
import org.inventivetalent.pluginannotations.command.Permission;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class SkullGallery extends JavaPlugin implements Listener {

	final String title = "§6Skull Gallery";

	Executor connectionExecutor = Executors.newSingleThreadExecutor();

	@Override
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, this);
		PluginAnnotations.loadAll(this, this);
	}

	@Command(name = "skullGallery",
			 aliases = {
					 "sg",
					 "openSkullGallery" },
			 usage = "[page]",
			 description = "Open the skull gallery",
			 min = 0,
			 max = 1,
			 fallbackPrefix = "skullgallery")
	@Permission("skullgallery.open")
	public void skullGallery(final Player sender, @OptionalArg(def = "1") Integer page) {
		page = Math.max(page, 1);
		sender.sendMessage("§7Loading page #" + page + "...");

		final Inventory inventory = Bukkit.createInventory(null, 9 * 6, title);

		final Integer finalPage = page;
		connectionExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					URL pagesUrl = new URL("http://skulls.inventivetalent.org/api/geturls.php?page=" + finalPage);
					HttpURLConnection pagesConnection = (HttpURLConnection) pagesUrl.openConnection();
					pagesConnection.setRequestProperty("User-Agent", "SkullGallery");
					JsonObject pagesObject = new JsonParser().parse(new InputStreamReader(pagesConnection.getInputStream())).getAsJsonObject();

					sender.openInventory(inventory);

					JsonArray pageArray = pagesObject.getAsJsonArray("urls");
					for (JsonElement pageElement : pageArray) {
						final int id = pageElement.getAsJsonObject().get("id").getAsInt();
						connectionExecutor.execute(new Runnable() {
							@Override
							public void run() {
								try {
									URL skullUrl = new URL("http://skulls.inventivetalent.org/api/getid.php?id=" + id);
									HttpURLConnection skullConnection = (HttpURLConnection) skullUrl.openConnection();
									skullConnection.setRequestProperty("User-Agent", "SkullGallery");
									JsonObject skullObject = new JsonParser().parse(new InputStreamReader(skullConnection.getInputStream())).getAsJsonObject();
									JsonObject skullData = skullObject.getAsJsonObject("data");
									JsonObject skullTexture = skullData.getAsJsonObject("properties").getAsJsonArray("textures").get(0).getAsJsonObject();

									ItemStack itemStack = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
									SkullMeta skullMeta = (SkullMeta) itemStack.getItemMeta();
									skullMeta.setOwner("MHF_custom_skull");
									skullMeta.setDisplayName("#" + id);
									HeadTextureChanger.applyTextureToMeta(skullMeta, HeadTextureChanger.createProfile(skullTexture.get("value").getAsString(), skullTexture.get("signature").getAsString()));
									itemStack.setItemMeta(skullMeta);

									inventory.addItem(itemStack);
								} catch (IOException e) {
									getLogger().log(Level.WARNING, "IOException while connecting to the skull API for ID #" + id, e);
								} catch (Exception e) {
									getLogger().log(Level.SEVERE, "Unexpected exception", e);
								}
							}
						});
					}

					JsonObject pageInfoObject = pagesObject.getAsJsonObject("page");
					int pageIndex = pageInfoObject.get("index").getAsInt();
					int pageCount = pageInfoObject.get("count").getAsInt();
					if (pageIndex > 1) {
						ItemStack itemStack = new ItemStack(Material.ARROW);
						ItemMeta meta = itemStack.getItemMeta();
						meta.setDisplayName("§bPrevious page");
						meta.setLore(Arrays.asList((pageIndex - 1) + "/" + pageCount));
						itemStack.setItemMeta(meta);
						inventory.setItem(45, itemStack);
					}
					if (pageIndex < pageCount) {
						ItemStack itemStack = new ItemStack(Material.ARROW);
						ItemMeta meta = itemStack.getItemMeta();
						meta.setDisplayName("§bNext page");
						meta.setLore(Arrays.asList((pageIndex + 1) + "/" + pageCount));
						itemStack.setItemMeta(meta);
						inventory.setItem(53, itemStack);
					}
				} catch (IOException e) {
					getLogger().log(Level.WARNING, "IOException while connecting to the skull API", e);
				}
			}
		});
	}

	@EventHandler
	public void on(InventoryClickEvent event) {
		if (event.getClickedInventory() == null) { return; }
		if (title.equals(event.getClickedInventory().getTitle())) {
			ItemStack itemStack = event.getCurrentItem();
			if (itemStack == null) {
				itemStack = event.getCursor();
			}
			if (itemStack != null) {
				event.setCancelled(true);
				if (itemStack.hasItemMeta()) {
					if (itemStack.getItemMeta().getDisplayName() != null) {
						if (itemStack.getItemMeta().getDisplayName().startsWith("#")) {
							if (event.getClick() == ClickType.LEFT) {
								event.setCursor(itemStack.clone());
							} else if (event.getClick() == ClickType.RIGHT) {
								event.getWhoClicked().sendMessage("https://skulls.inventivetalent.org/" + itemStack.getItemMeta().getDisplayName().substring(1));
							} else if (event.getClick() == ClickType.SHIFT_LEFT) {
								ItemStack clone = itemStack.clone();
								clone.setAmount(64);
								event.getWhoClicked().getInventory().addItem(clone);
							} else if (event.getClick() == ClickType.SHIFT_RIGHT) {
								event.getWhoClicked().getInventory().addItem(itemStack.clone());

							}
						}
						if ("§bPrevious page".equals(itemStack.getItemMeta().getDisplayName())) {
							String page = itemStack.getItemMeta().getLore().get(0).split("/")[0];
							event.getWhoClicked().closeInventory();
							((Player) event.getWhoClicked()).chat("/skullGallery " + page);
						}
						if ("§bNext page".equals(itemStack.getItemMeta().getDisplayName())) {
							String page = itemStack.getItemMeta().getLore().get(0).split("/")[0];
							event.getWhoClicked().closeInventory();
							((Player) event.getWhoClicked()).chat("/skullGallery " + page);
						}
					}
				}
			}
		}
	}

}
