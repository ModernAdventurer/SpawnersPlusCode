package me.LoneSurvivor.SpawnersPlus;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import me.LoneSurvivor.SpawnersPlus.Files.DataManager;
import net.milkbowl.vault.economy.Economy;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;

public class SpawnersPlus extends JavaPlugin implements Listener {
	Economy eco;
	DecimalFormat df = new DecimalFormat("0.00");
	public Inventory inv;
	public static Map<Location, int[]> SpawnerLevels = new HashMap<Location, int[]>();
	public static Map<Player, Location> OpenSpawnerLocation = new HashMap<Player, Location>();
	public DataManager data;
	/**
	 * sets up economy and retrieves spawners from the data config 
	 * by moving them to the hashmap on server startup and then
	 * wipes the data file because it usually takes up alot of data
	 * if there are lots of spawners
	 */
	@Override
	public void onEnable() {
		this.data = new DataManager(this);
		data.saveDefaultConfig();	
		this.saveDefaultConfig();
		this.getServer().getPluginManager().registerEvents(this, this);
		if(!setupEconomy()) {
			getServer().getPluginManager().disablePlugin(this);
		}
		if(data.getConfig().getConfigurationSection("spawner-levels").getKeys(false).size() != 0) {
			data.getConfig().getConfigurationSection("spawner-levels").getKeys(false).forEach(key -> {
				int[] content = stringArrayToIntArray(data.getConfig().getString("spawner-levels." + key).split(":"));
				final String[] locationparts = key.split(":");
				Location location = new Location(Bukkit.getServer().getWorld(locationparts[0]), Integer.parseInt(locationparts[1]), 
						Integer.parseInt(locationparts[2]), Integer.parseInt(locationparts[3]));
				SpawnerLevels.put(location, content);
			});
			if (this.getConfig().getBoolean("clearDataFileOnLaunch")) {
				data.getConfig().set("spawner-levels", null);
			}
			data.saveConfig();
		}
	}
	/**
	 * Saves spawners from hashmap to data config on server shutdown
	 */
	@Override
	public void onDisable() {
		this.saveDefaultConfig();
		data.getConfig().set("spawner-levels", null);
		if(!SpawnerLevels.isEmpty()) {
			for (Map.Entry<Location, int[]> entry : SpawnerLevels.entrySet()) {
				Location l = entry.getKey();
				String SerializedLocation = l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
				String SerializedLevels = entry.getValue()[0] + ":" + entry.getValue()[1] + ":" + entry.getValue()[2];
				data.getConfig().set("spawner-levels." + SerializedLocation, SerializedLevels);
			}
			data.saveConfig();
		}
	}
	
	private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        eco = rsp.getProvider();
        return eco != null;
    }
	
	/**
	 * This opens the spawner GUI when someone right clicks on the spawner
	 * if there isnt already a stored set of values for the spawner 
	 * in the spawners hashmap it adds that spawner to the hashmap with the
	 * default values level 1 speed, level 1 quantity, & level 1 drops
	 */
	
	@EventHandler()
	public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action.equals(Action.RIGHT_CLICK_BLOCK)) {
            Player player = event.getPlayer();
            Location location = event.getClickedBlock().getLocation(); //possible error
            final Block block = event.getClickedBlock();
            if(block.getType().equals(Material.SPAWNER)) {
            	if(!player.isSneaking()) {
            		event.setCancelled(true);
	            	//On Right Click Spawner
	            	if(!SpawnerLevels.containsKey(location)) {
	            		this.newSpawnerAt(location);
	            	} else {
	            		this.updateSpawnerStats(location);
	            	}
	            	OpenSpawnerLocation.put(player, location);
	                this.openSpawnerGUI(player, SpawnerLevels.get(location));
            	}
            }
        }
	}
	
	/**
	 * When an entity spawns if the spawner is not on the spawner list add 
	 * it and then then set the entities name to the drop level of the spawner
	 * + its mob type. Example: Level 3 Villager
	 * @param plugin 
	 */
	
	@EventHandler()
	public void onMobSpawn(SpawnerSpawnEvent event) {
		if(!SpawnerLevels.containsKey(event.getSpawner().getLocation())) {
			newSpawnerAt(event.getSpawner().getLocation());
		}
		this.updateSpawnerStats(event.getSpawner().getLocation());
		int[] levels = SpawnerLevels.get(event.getSpawner().getLocation());
		event.getEntity().setCustomName(ChatColor.translateAlternateColorCodes('&', "&f&lLevel " + levels[2] + " " + capitalizeString(event.getEntityType().toString().replace('_', ' '))));
		event.getEntity().setCustomNameVisible(true);
	}
    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
    	if(event.getEntity().isCustomNameVisible()) {
    		//Extracts level from Custom Name
    		String step1 = this.stripcolor(event.getEntity().getName().replaceAll("" + capitalizeString(event.getEntityType().toString().replace('_', ' ')), "").replaceAll(" ", "").replaceAll("Level", ""));
    		String step2 = "";
    		for(int i=2; i<=step1.length()-1; i++) {
    			step2 = step2 + step1.charAt(i);
    		}
    		int level = Integer.parseInt(step2);
    		EntityType entity = event.getEntityType();
    		event.getDrops().clear();
    		//If there are drops programmed in the config add drops
        	if(!this.getConfig().isString("Drop.Drops." + entity)) {
        		ArrayList<ItemStack> drops = getDrops(entity, level);
        		event.getDrops().addAll(drops);
        	}
    	}
    }
    
    public ArrayList<ItemStack> getDrops(EntityType entity, int level) {
    	ArrayList<ItemStack> result = new ArrayList<ItemStack>();
    	Set<String> drops = this.getConfig().getConfigurationSection("Drop.Drops." + entity + "." + level).getKeys(false);
    	for(String dropString : drops) {
    		Material m = Material.matchMaterial(dropString);
    		int q = this.getConfig().getInt("Drop.Drops." + entity + "." + level + "." + dropString);
    		ItemStack drop = new ItemStack(m, q);
        	result.add(drop);
    	}
		return result;
    }
    
	@EventHandler()
	@SuppressWarnings("deprecation")
	public void onBlockBreak(BlockBreakEvent event) {
		Location location = event.getBlock().getLocation();
		if (event.getBlock().getType().equals(Material.SPAWNER)) {
			if(event.getPlayer().getItemInHand().containsEnchantment(Enchantment.SILK_TOUCH)) {
				if(event.getPlayer().getGameMode() == GameMode.SURVIVAL) {
					int[] levels = new int[]{1,1,1};
					if(SpawnerLevels.containsKey(location)) {
						levels = SpawnerLevels.get(location);
					}
			    	CreatureSpawner cs = (CreatureSpawner) event.getBlock().getState();
			    	EntityType entity = cs.getSpawnedType();
					event.setExpToDrop(0);
					event.setDropItems(false);
					ItemStack item = new ItemStack(event.getBlock().getType(), 1);
					BlockStateMeta bsm = (BlockStateMeta) item.getItemMeta();
					BlockState bs = bsm.getBlockState();
					((CreatureSpawner) bs).setSpawnedType(entity);
					bsm.setBlockState(bs);
					item.setItemMeta(bsm);
					ItemMeta meta = item.getItemMeta();
					List<String> lore = new ArrayList<String>();
					meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&b" + capitalizeString(entity.toString().replace('_', ' ')) + " Spawner"));
					lore.clear();
					lore.add(ChatColor.translateAlternateColorCodes('&', "&8----------------"));
					lore.add(ChatColor.translateAlternateColorCodes('&', "&bType: &6" + recap(capitalizeString(entity.toString().replace('_', ' ')))));
					lore.add(ChatColor.translateAlternateColorCodes('&', "&bSpeed Level: &6" + levels[0]));
					lore.add(ChatColor.translateAlternateColorCodes('&', "&bQuantity Level: &6" + levels[1]));
					lore.add(ChatColor.translateAlternateColorCodes('&', "&bDrop Level: &6" + levels[2]));
					lore.add(ChatColor.translateAlternateColorCodes('&', "&8----------------"));
					meta.setLore(lore);
					item.setItemMeta(meta);
					location.getWorld().dropItem(location, item);
				}
			}
			SpawnerLevels.remove(location);
		}
	}
	@EventHandler()
	@SuppressWarnings("deprecation")
	public void onBlockPlace(BlockPlaceEvent event) {
		ItemStack item = event.getItemInHand();
		if (event.getBlock().getType().equals(Material.SPAWNER)) {
			if(item.hasItemMeta()) {
				List<String> lore = event.getItemInHand().getItemMeta().getLore();
				CreatureSpawner cs = (CreatureSpawner) event.getBlock().getState();
				EntityType entity;
				try {
					entity = EntityType.fromName(ChatColor.stripColor(lore.get(1)).replace("Type: ", "").replace(' ', '_').toUpperCase());
				} catch (Exception e) {
					event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', "&4Error: &cUnknown Entity"));
					entity = EntityType.PIG;
				}
				cs.setSpawnedType(entity);
				cs.update();
				int speed = Integer.parseInt(ChatColor.stripColor(lore.get(2)).replace("Speed Level: ", ""));
				int quantity = Integer.parseInt(ChatColor.stripColor(lore.get(3)).replace("Quantity Level: ", ""));
				int drop = Integer.parseInt(ChatColor.stripColor(lore.get(4)).replace("Drop Level: ", ""));
				int[] levels = new int[] {speed, quantity, drop};
				SpawnerLevels.put(event.getBlock().getLocation(), levels);
				this.updateSpawnerStats(event.getBlock().getLocation());
				return;
			}
			if(!SpawnerLevels.containsKey(event.getBlock().getLocation())) {
				newSpawnerAt(event.getBlock().getLocation());
			}
		}
	}
	/**
	 * This Part of the code controls what happens 
	 * when someone clicks a button in the Spawner Upgrade GUI
	 */
	@EventHandler()
	public void onClick(InventoryClickEvent event) {
		Player player = (Player) event.getWhoClicked();
		Location location = OpenSpawnerLocation.get(player);
		if(!event.getInventory().equals(inv)) return;
		if(event.getCurrentItem()==null) return;
		if(event.getCurrentItem().getItemMeta()==null) return;
		if(event.getCurrentItem().getItemMeta().getDisplayName() == null) return;
		event.setCancelled(true);
		
		//if speed button clicked
		if(event.getCurrentItem().getItemMeta().getDisplayName().contains("Speed")) {
			int[] levels = SpawnerLevels.get(location);
			//if level cap has not been reached or exceeded
			if(levels[0]+1<=this.getConfig().getInt("Speed.UpgradeCap")) {
				//calculate price based on level and price multiplier
				double price = calculatePrice(MultiplyNumTimes(this.getConfig().getDouble("Speed.StartingPrice"), this.getConfig().getDouble("Speed.PriceMultiplier"), levels[0]-1));
				//if enough money
				if(eco.getBalance(player)>=price) {
					//Withdraw money
					eco.withdrawPlayer(player, price);
					//Increase level
					levels[0]+=1;
					//Update GUI
					SpawnerLevels.put(location, levels);
					//Refresh players GUI
					this.openSpawnerGUI(player, SpawnerLevels.get(location));
					//Send player success message
					player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aSpawner speed upgraded for " + price + "$"));
					player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aNew balance: " + eco.getBalance(player) + "$"));
					this.updateSpawnerStats(location);
				} else {
					player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cYou dont have enough money!"));
				}
			}
			return;
		}
		if(event.getCurrentItem().getItemMeta().getDisplayName().contains("Quantity")) {
			int[] levels = SpawnerLevels.get(location);
			if(levels[1]+1<=this.getConfig().getInt("Quantity.UpgradeCap")) {
				double price = calculatePrice(MultiplyNumTimes(this.getConfig().getDouble("Quantity.StartingPrice"), this.getConfig().getDouble("Quantity.PriceMultiplier"), levels[1]-1));
				if(eco.getBalance(player)>=price) {
					eco.withdrawPlayer(player, price);
					levels[1]+=1;
					SpawnerLevels.put(location, levels);
					this.openSpawnerGUI(player, SpawnerLevels.get(location));
					//Send player success message
					player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aSpawner quantity upgraded for " + price + "$"));
					player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aNew balance: " + eco.getBalance(player) + "$"));
					this.updateSpawnerStats(location);
				} else {
					player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cYou dont have enough money!"));
					//replace with redstone block that says "&cnot enough money" for 3 seconds
				}
			}
			return;
		}
		if(event.getCurrentItem().getItemMeta().getDisplayName().contains("Drop")) {
			int[] levels = SpawnerLevels.get(location);
			if(levels[2]+1<=this.getConfig().getInt("Drop.UpgradeCap")) {
				double price = calculatePrice(MultiplyNumTimes(this.getConfig().getDouble("Drop.StartingPrice"), this.getConfig().getDouble("Drop.PriceMultiplier"), levels[2]-1));
				if(eco.getBalance(player)>=price) {
					eco.withdrawPlayer(player, price);
					levels[2]+=1;
					SpawnerLevels.put(location, levels);
					this.openSpawnerGUI(player, SpawnerLevels.get(location));
					//Send player success message
					player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aSpawner drops upgraded for " + price + "$"));
					player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aNew balance: " + eco.getBalance(player) + "$"));
					this.updateSpawnerStats(location);
				} else {
					player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cYou dont have enough money!"));
					//replace with redstone block that says "&cnot enough money" for 3 seconds
				}
			}
			return;
		}
		if(event.getCurrentItem().getItemMeta().getDisplayName().contains("Close Menu")) {
			player.closeInventory();
			return;
		}
	}
	/**
	 * This Part of the code is related to constructing the Spawner Upgrade GUI
	 */
	public void openSpawnerGUI(Player player, int[] levels) {
		//get EntityType from location
		Location location = OpenSpawnerLocation.get(player);
    	CreatureSpawner cs = (CreatureSpawner) location.getBlock().getState();
    	EntityType entity = cs.getSpawnedType();
		if(player.hasPermission("spawnersplus.usespawner") || player.hasPermission("spawnersplus.usespawner." + entity.toString().toLowerCase())) {
			player.closeInventory();
			
			//Create new inventory
			ItemStack item = new ItemStack(Material.WATER);
			ItemMeta meta = item.getItemMeta();
			List<String> lore = new ArrayList<String>();
			int slot = 0;
			int guisize = 27;
			String title = ChatColor.translateAlternateColorCodes('&', "&8&lSpawner Upgrade GUI");
			inv = Bukkit.createInventory(null, guisize, title);

			//fill blank spaces in GUI with filler block
			for(slot=0; slot<guisize-1; slot++) {
				Material material = Material.GRAY_STAINED_GLASS_PANE;
				item.setType(material);
				//Sets Name
				meta.setDisplayName(" ");
				//Sets Description
				lore.clear();
				meta.setLore(lore);
				//Applies changes
				item.setItemMeta(meta);
				inv.setItem(slot, item);
			}
			
			//Speed Upgrade
			item.setType(Material.SEA_LANTERN);
			int levelcap=this.getConfig().getInt("Speed.UpgradeCap");
			double defaultValue=this.getConfig().getDouble("Speed.DefaultSpeed");
			double multiplier=this.getConfig().getDouble("Speed.TimerMultiplier");
			meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&bSpeed Level: &6" + levels[0]));
			lore.clear();
			lore.add(ChatColor.translateAlternateColorCodes('&', "&8----------------"));
			if(levels[0]+1<=levelcap) {
				lore.add(ChatColor.translateAlternateColorCodes('&', "&bLevel: &6" + levels[0] + " &b-> &6" + (levels[0]+1)));
				lore.add(ChatColor.translateAlternateColorCodes('&', "&bSpeed: &6" + Math.round((MultiplyNumTimes(defaultValue, multiplier, levels[0]-1))) + "s &b-> &6" + Math.round(MultiplyNumTimes(defaultValue, multiplier, levels[0])) + "s"));
				lore.add(ChatColor.translateAlternateColorCodes('&', "&8----------------"));
				double price = calculatePrice(MultiplyNumTimes(this.getConfig().getDouble("Speed.StartingPrice"), this.getConfig().getDouble("Speed.PriceMultiplier"), levels[0]-1));
				lore.add(ChatColor.translateAlternateColorCodes('&', "&bPrice: &6" + price));
			} else {
				lore.add(ChatColor.translateAlternateColorCodes('&', "&bLevel: &6" + levels[0]));
				lore.add(ChatColor.translateAlternateColorCodes('&', "&bSpeed: &6" + Math.round((MultiplyNumTimes(defaultValue, multiplier, levels[0]-1))) + "s"));
				lore.add(ChatColor.translateAlternateColorCodes('&', "&8----------------"));
				lore.add(ChatColor.translateAlternateColorCodes('&', "&bPrice: &cMax Level"));
			}
			meta.setLore(lore);
			item.setItemMeta(meta);
			inv.setItem(11, item);
			
			//Quantity Upgrade
			item.setType(Material.MAGMA_BLOCK);
			levelcap=this.getConfig().getInt("Quantity.UpgradeCap");
			defaultValue=this.getConfig().getDouble("Quantity.DefaultQuantity");
			multiplier=this.getConfig().getDouble("Quantity.QuantityPerLevel");
			meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&bQuantity Level: &6" + levels[1]));
			lore.clear();
			lore.add(ChatColor.translateAlternateColorCodes('&', "&8----------------"));
			if(levels[1]+1<=levelcap) {
				lore.add(ChatColor.translateAlternateColorCodes('&', "&bLevel: &6" + levels[1] + " &b-> &6" + (levels[1]+1)));
				lore.add(ChatColor.translateAlternateColorCodes('&', "&bQuantity: &6" + (defaultValue+(levels[1]-1*multiplier)) + "&b " + capitalizeString(entity.toString().replace('_', ' ')) +"'s -> &6" + (defaultValue+(levels[1]*multiplier)) + "&b " + capitalizeString(entity.toString().replace('_', ' ')) + "'s"));
				lore.add(ChatColor.translateAlternateColorCodes('&', "&8----------------"));
				double price = calculatePrice(MultiplyNumTimes(this.getConfig().getDouble("Quantity.StartingPrice"), this.getConfig().getDouble("Quantity.PriceMultiplier"), levels[1]-1));
				lore.add(ChatColor.translateAlternateColorCodes('&', "&bPrice: &6" + price));
			} else {
				lore.add(ChatColor.translateAlternateColorCodes('&', "&bLevel: &6" + levels[1]));
				lore.add(ChatColor.translateAlternateColorCodes('&', "&bQuantity: &6" + (defaultValue+(levels[1]-1*multiplier)) + "&b " + capitalizeString(entity.toString().replace('_', ' ')) + "'s"));
				lore.add(ChatColor.translateAlternateColorCodes('&', "&8----------------"));
				lore.add(ChatColor.translateAlternateColorCodes('&', "&bPrice: &cMax Level"));
			}
			meta.setLore(lore);
			item.setItemMeta(meta);
			inv.setItem(13, item);
			
			//Drop Upgrade
        	if(!this.getConfig().isString("Drop.Drops." + entity)) {
    			item.setType(Material.IRON_BLOCK);
    			levelcap=this.getConfig().getInt("Drop.UpgradeCap");
    			meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&bDrop Level: &6" + levels[2]));
    			lore.clear();
    			lore.add(ChatColor.translateAlternateColorCodes('&', "&8----------------"));
    			if(levels[2]+1<=levelcap) {
    				lore.add(ChatColor.translateAlternateColorCodes('&', "&bLevel: &6" + levels[2] + " &b-> &6" + (levels[2]+1)));
    				lore.add(ChatColor.translateAlternateColorCodes('&', "&bDrop:"));
    		    	//Calculate Drops
    		    	ArrayList<ItemStack> beforelist = getDrops(entity, levels[2]);
    		    	ArrayList<ItemStack> afterlist = getDrops(entity, levels[2]+1);
    		    	//Print Data
    		    	for(int i = 0; i<beforelist.size(); i++) {
    		    		ItemStack before = beforelist.get(i);
    		    		ItemStack after = afterlist.get(i);
    					lore.add(ChatColor.translateAlternateColorCodes('&', "&6"+before.getAmount()+"x&b "+capitalizeString(before.getType().toString().replace('_', ' '))+" -> &6"+after.getAmount()+"x&b "+capitalizeString(before.getType().toString().replace('_', ' '))));
    		    	}
    				lore.add(ChatColor.translateAlternateColorCodes('&', "&8----------------"));
    				double price = calculatePrice(MultiplyNumTimes(this.getConfig().getDouble("Drop.StartingPrice"), this.getConfig().getDouble("Drop.PriceMultiplier"), levels[2]-1));
    				lore.add(ChatColor.translateAlternateColorCodes('&', "&bPrice: &6" + price));
    		    	
    			} else {
    				lore.add(ChatColor.translateAlternateColorCodes('&', "&bLevel: &6" + levels[2]));
    				lore.add(ChatColor.translateAlternateColorCodes('&', "&bDrop:"));
    		    	//Calculate Drops
    		    	ArrayList<ItemStack> beforelist = getDrops(entity, levels[2]);
    		    	//Print Data
    		    	for(int i = 0; i<beforelist.size(); i++) {
    		    		ItemStack before = beforelist.get(i);
    					lore.add(ChatColor.translateAlternateColorCodes('&', "&6"+before.getAmount()+"x&b "+capitalizeString(before.getType().toString().replace('_', ' '))));
    		    	}
    				lore.add(ChatColor.translateAlternateColorCodes('&', "&8----------------"));
    				lore.add(ChatColor.translateAlternateColorCodes('&', "&bPrice: &cMax Level"));
    			}
    			meta.setLore(lore);
    			item.setItemMeta(meta);
    			inv.setItem(15, item);
    			
        	}
			//Close Menu Button
			item.setType(Material.BARRIER);
			meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&4&lClose Menu"));
			lore.clear();
			meta.setLore(lore);
			item.setItemMeta(meta);
			inv.setItem(guisize-1, item);
			
			player.openInventory(inv);
		}
	}
	
	/**
	 * These are functions that are often repeated
	 */
	public int[] stringArrayToIntArray(String[] a) {
		int[] numbers = new int[a.length];
		for(int i = 0;i < a.length;i++)
		{
		   numbers[i] = Integer.parseInt(a[i]);
		}
		return numbers;
	}

	public double MultiplyNumTimes(double num, double multiply, int times) {
		for(int i = 0; i!=times; i++) {
			num=num*multiply;
		}
		return num;
	}
	
	public double round(double input) {
		return Math.round(input);
	}
	
	public double calculatePrice(double price) {
		return Double.parseDouble(df.format(price));
	}

	//Functions that use spawner at a specified location
	public void newSpawnerAt(Location location) {
		if (location.getBlock().getState() instanceof CreatureSpawner) {
			int[] defaultlevels = new int[] {1,1,1};
			SpawnerLevels.put(location, defaultlevels);
			this.updateSpawnerStats(location);
		}
	}
	private final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)" + String.valueOf('&') + "[0-9A-FK-OR]");
	public String stripcolor(String input) {
        return input == null?null:STRIP_COLOR_PATTERN.matcher(input).replaceAll("");
    }
	
	public void announceList(List<String> a) {
		Bukkit.broadcastMessage("AnnounceListStart");
		for(String bc : a) {
			Bukkit.broadcastMessage(bc);
		}
	}
	
	public void updateSpawnerStats(Location location) {
		int[] levels = SpawnerLevels.get(location);
    	CreatureSpawner cs = (CreatureSpawner) location.getBlock().getState();
    	int speed=20*(int) Math.round(MultiplyNumTimes(this.getConfig().getDouble("Speed.DefaultSpeed"), this.getConfig().getDouble("Speed.TimerMultiplier"), levels[0]-1));
    	int quantity=this.getConfig().getInt("Quantity.DefaultQuantity")+(levels[1]-1*this.getConfig().getInt("Quantity.QuantityPerLevel"));
    	cs.setDelay(speed);
    	if(cs.getMaxSpawnDelay()<speed) {
        	cs.setMaxSpawnDelay(speed+this.getConfig().getInt("Speed.MaxSpeedVariation"));
        	cs.setMinSpawnDelay(speed-this.getConfig().getInt("Speed.MaxSpeedVariation"));
    	} else {
        	cs.setMinSpawnDelay(speed-this.getConfig().getInt("Speed.MaxSpeedVariation"));
        	cs.setMaxSpawnDelay(speed+this.getConfig().getInt("Speed.MaxSpeedVariation"));
    	}
    	cs.setSpawnCount(quantity);
    	cs.update();
	}
	public String recap(String input) {
		return input.toLowerCase().substring(0, 1).toUpperCase() + input.toLowerCase().substring(1);
	}
	
	public static String capitalizeString(String string) {
		char[] chars = string.toLowerCase().toCharArray();
		boolean found = false;
		for (int i = 0; i < chars.length; i++) {
			if (!found && Character.isLetter(chars[i])) {
				chars[i] = Character.toUpperCase(chars[i]);
				found = true;
			} else if (Character.isWhitespace(chars[i]) || chars[i]==' ') { // You can add other chars here
				found = false;
			}
		}
		return String.valueOf(chars);
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(label.equalsIgnoreCase("spawnersplus") || label.equalsIgnoreCase("sp")) {
			if(sender instanceof Player) {
				Player player = (Player) sender;
				if(args.length > 0) {
					if(args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("?")) {
						if(player.hasPermission("spawnersplus.help")) {
							player.sendMessage(ChatColor.GRAY + "-------------------");
							player.sendMessage(ChatColor.GREEN + "Spawners++ Commands");
							player.sendMessage(ChatColor.GRAY + "-------------------");
							if(player.hasPermission("spawnersplus.givespawner")) {
								player.sendMessage(ChatColor.GREEN + "/sp givespawner <Amount> <EntityType>");
							}
							if(player.hasPermission("spawnersplus.help")) {
								player.sendMessage(ChatColor.GREEN + "/sp help");
							}
							if(player.hasPermission("spawnersplus.reload")) {
								player.sendMessage(ChatColor.GREEN + "/sp reload");
							}
						} else {
							player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&4Error: &cInsufficient Permission"));
							return false;
						}
						return false;
					}
					if(args[0].equalsIgnoreCase("reload") || args[0].equalsIgnoreCase("rl")) {
						if(player.hasPermission("spawnersplus.reload")) {
							this.saveDefaultConfig();
							this.reloadConfig();
							player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&f[&6Spawners&b++&f] &aConfig reloaded!"));
							return true; 
						} else {
							player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&4Error: &cInsufficient Permission"));
						}
					}
					if(args[0].equalsIgnoreCase("givespawner") || args[0].equalsIgnoreCase("gs") || args[0].equalsIgnoreCase("spawner") || args[0].equalsIgnoreCase("give")) {
						if(player.hasPermission("spawnersplus.givespawner")) {
							if(args.length>=2) {
								if(!(isNum(args[2]))) {
									int[] levels = new int[]{1,1,1};
									try {
										String input="";
										for(String arg : args) {
											if(arg!=args[0] && arg!=args[1]) {
												if(arg!=args[2]) {
													input = input + "_";
												}
												input = input + arg.toUpperCase();
											}
										}
										@SuppressWarnings("deprecation")
										EntityType entity = EntityType.fromName(input);
										ItemStack item = new ItemStack(Material.SPAWNER, Integer.parseInt(args[1]));
										BlockStateMeta bsm = (BlockStateMeta) item.getItemMeta();
										BlockState bs = bsm.getBlockState();
										((CreatureSpawner) bs).setSpawnedType(entity);
										bsm.setBlockState(bs);
										item.setItemMeta(bsm);
										ItemMeta meta = item.getItemMeta();
										List<String> lore = new ArrayList<String>();
										meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&b" + capitalizeString(entity.toString().replace('_', ' ')) + " Spawner"));
										lore.clear();
										lore.add(ChatColor.translateAlternateColorCodes('&', "&8----------------"));
										lore.add(ChatColor.translateAlternateColorCodes('&', "&bType: &6" + recap(capitalizeString(entity.toString().replace('_', ' ')))));
										lore.add(ChatColor.translateAlternateColorCodes('&', "&bSpeed Level: &6" + levels[0]));
										lore.add(ChatColor.translateAlternateColorCodes('&', "&bQuantity Level: &6" + levels[1]));
										lore.add(ChatColor.translateAlternateColorCodes('&', "&bDrop Level: &6" + levels[2]));
										lore.add(ChatColor.translateAlternateColorCodes('&', "&8----------------"));
										meta.setLore(lore);
										item.setItemMeta(meta);
										player.getInventory().addItem(item);
									} catch (Exception e) {
										player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&4Error: &cUnknown EntityType"));
										return false;
									}
								} else {
									player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&4Error: &cIncorrect Format, Please use: /sp givespawner <Amount> <EntityType>"));
									return false;
								}
							} else {
								player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&4Error: &cNot Enough Parameters, Please use: /sp givespawner <Amount> <EntityType>"));
								return false;
							}
						} else {
							player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&4Error: &cInsufficient Permission"));
							return false;
						}
						return false;
					}
				}
			} else {
				System.out.println("Console cannot use this command");
				return false;
			}
		}
		return false;
	}
	public boolean isNum(String num) {
		try {
			Integer.parseInt(num);
		} catch (Exception e) {
			return false;
		}
		return true;
	}
}
