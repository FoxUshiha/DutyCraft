package com.foxsrv.dutycraft;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DutyCraft extends JavaPlugin implements Listener, TabCompleter {

    private File dutiesFile;
    private FileConfiguration dutiesConfig;
    private File playerDataFile;
    private FileConfiguration playerDataConfig;
    
    private Map<String, Duty> duties = new ConcurrentHashMap<>();
    private Map<UUID, PlayerDutyData> playerDutyData = new ConcurrentHashMap<>();
    
    // NBT Key para identificar itens do duty
    private NamespacedKey dutyItemKey;
    private NamespacedKey dutyNameKey;
    
    // Lista de materiais que são considerados "comestíveis" ou "utilizáveis"
    private List<Material> allowedUsableMaterials = new ArrayList<>();
    
    // Slots da armadura
    private final int[] ARMOR_SLOTS = {36, 37, 38, 39}; // Boots, Leggings, Chestplate, Helmet
    
    @Override
    public void onEnable() {
        // Inicializar NBT keys
        dutyItemKey = new NamespacedKey(this, "duty_item");
        dutyNameKey = new NamespacedKey(this, "duty_name");
        
        // Criar pasta do plugin
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        
        // Criar arquivos de configuração
        createFiles();
        
        // Registrar tab completer
        if (getCommand("setduty") != null) {
            getCommand("setduty").setTabCompleter(this);
        }
        if (getCommand("unsetduty") != null) {
            getCommand("unsetduty").setTabCompleter(this);
        }
        if (getCommand("duty") != null) {
            getCommand("duty").setTabCompleter(this);
        }
        
        // Carregar dados
        loadDuties();
        loadPlayerData();
        
        // Registrar eventos
        getServer().getPluginManager().registerEvents(this, this);
        
        // Configurar materiais permitidos
        setupAllowedMaterials();
        
        // Iniciar scanner de inventário
        startInventoryScanner();
        
        getLogger().info("DutyCraft has been enabled successfully!");
        getLogger().info("Loaded " + duties.size() + " duties!");
    }
    
    @Override
    public void onDisable() {
        // Salvar dados
        savePlayerData();
        saveDuties();
        
        getLogger().info("DutyCraft has been disabled!");
    }
    
    private void createFiles() {
        // Arquivo de duties
        dutiesFile = new File(getDataFolder(), "duties.yml");
        if (!dutiesFile.exists()) {
            try {
                dutiesFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dutiesConfig = YamlConfiguration.loadConfiguration(dutiesFile);
        
        // Arquivo de dados dos jogadores
        playerDataFile = new File(getDataFolder(), "playerdata.yml");
        if (!playerDataFile.exists()) {
            try {
                playerDataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
    }
    
    private void setupAllowedMaterials() {
        // Materiais que podem ser usados/interagidos
        allowedUsableMaterials.add(Material.COOKED_BEEF);
        allowedUsableMaterials.add(Material.BREAD);
        allowedUsableMaterials.add(Material.APPLE);
        allowedUsableMaterials.add(Material.GOLDEN_APPLE);
        allowedUsableMaterials.add(Material.ENCHANTED_GOLDEN_APPLE);
        allowedUsableMaterials.add(Material.CARROT);
        allowedUsableMaterials.add(Material.POTATO);
        allowedUsableMaterials.add(Material.BAKED_POTATO);
        allowedUsableMaterials.add(Material.POISONOUS_POTATO);
        allowedUsableMaterials.add(Material.BEETROOT);
        allowedUsableMaterials.add(Material.BEETROOT_SOUP);
        allowedUsableMaterials.add(Material.MUSHROOM_STEW);
        allowedUsableMaterials.add(Material.RABBIT_STEW);
        allowedUsableMaterials.add(Material.COOKED_CHICKEN);
        allowedUsableMaterials.add(Material.COOKED_MUTTON);
        allowedUsableMaterials.add(Material.COOKED_PORKCHOP);
        allowedUsableMaterials.add(Material.COOKED_RABBIT);
        allowedUsableMaterials.add(Material.COOKED_COD);
        allowedUsableMaterials.add(Material.COOKED_SALMON);
        allowedUsableMaterials.add(Material.PUMPKIN_PIE);
        allowedUsableMaterials.add(Material.MELON_SLICE);
        allowedUsableMaterials.add(Material.SWEET_BERRIES);
        allowedUsableMaterials.add(Material.GLOW_BERRIES);
        allowedUsableMaterials.add(Material.CHORUS_FRUIT);
        allowedUsableMaterials.add(Material.DRIED_KELP);
        allowedUsableMaterials.add(Material.COOKIE);
        allowedUsableMaterials.add(Material.HONEY_BOTTLE);
        allowedUsableMaterials.add(Material.MILK_BUCKET);
        allowedUsableMaterials.add(Material.POTION);
        allowedUsableMaterials.add(Material.SPLASH_POTION);
        allowedUsableMaterials.add(Material.LINGERING_POTION);
        
        // Foguetes para elytra
        allowedUsableMaterials.add(Material.FIREWORK_ROCKET);
        allowedUsableMaterials.add(Material.FIREWORK_STAR);
        
        // Ferramentas e armas (podem ser usadas)
        allowedUsableMaterials.add(Material.WOODEN_SWORD);
        allowedUsableMaterials.add(Material.STONE_SWORD);
        allowedUsableMaterials.add(Material.IRON_SWORD);
        allowedUsableMaterials.add(Material.GOLDEN_SWORD);
        allowedUsableMaterials.add(Material.DIAMOND_SWORD);
        allowedUsableMaterials.add(Material.NETHERITE_SWORD);
        allowedUsableMaterials.add(Material.BOW);
        allowedUsableMaterials.add(Material.CROSSBOW);
        allowedUsableMaterials.add(Material.TRIDENT);
        allowedUsableMaterials.add(Material.SHIELD);
        
        // Arremessáveis
        allowedUsableMaterials.add(Material.SNOWBALL);
        allowedUsableMaterials.add(Material.EGG);
        allowedUsableMaterials.add(Material.ENDER_PEARL);
        allowedUsableMaterials.add(Material.ENDER_EYE);
    }
    
    private void loadDuties() {
        duties.clear();
        
        if (dutiesConfig.contains("duties")) {
            ConfigurationSection section = dutiesConfig.getConfigurationSection("duties");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    try {
                        Duty duty = new Duty(key);
                        duty.loadFromConfig(section.getConfigurationSection(key), this);
                        duties.put(key, duty);
                        getLogger().info("Loaded duty: " + key);
                    } catch (Exception e) {
                        getLogger().warning("Error loading duty '" + key + "': " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    
    private void saveDuties() {
        dutiesConfig.set("duties", null);
        
        for (Map.Entry<String, Duty> entry : duties.entrySet()) {
            ConfigurationSection section = dutiesConfig.createSection("duties." + entry.getKey());
            entry.getValue().saveToConfig(section);
        }
        
        try {
            dutiesConfig.save(dutiesFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void loadPlayerData() {
        playerDutyData.clear();
        
        if (playerDataConfig.contains("players")) {
            ConfigurationSection section = playerDataConfig.getConfigurationSection("players");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        PlayerDutyData data = new PlayerDutyData(uuid);
                        data.loadFromConfig(section.getConfigurationSection(key), this);
                        playerDutyData.put(uuid, data);
                    } catch (IllegalArgumentException e) {
                        getLogger().warning("Invalid UUID found: " + key);
                    }
                }
            }
        }
    }
    
    private void savePlayerData() {
        playerDataConfig.set("players", null);
        
        for (Map.Entry<UUID, PlayerDutyData> entry : playerDutyData.entrySet()) {
            ConfigurationSection section = playerDataConfig.createSection("players." + entry.getKey().toString());
            entry.getValue().saveToConfig(section);
        }
        
        try {
            playerDataConfig.save(playerDataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    // Serialização completa de ItemStack usando Base64
    public String serializeItemStack(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return "";
        
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            
            dataOutput.writeObject(item);
            dataOutput.close();
            
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }
    
    public ItemStack deserializeItemStack(String data) {
        if (data == null || data.isEmpty()) return null;
        
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            
            return item;
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public String serializeItemStackArray(ItemStack[] items) {
        if (items == null) return "";
        
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            
            dataOutput.writeInt(items.length);
            
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
            
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
            
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }
    
    public ItemStack[] deserializeItemStackArray(String data) {
        if (data == null || data.isEmpty()) return new ItemStack[0];
        
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            
            int length = dataInput.readInt();
            ItemStack[] items = new ItemStack[length];
            
            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            
            dataInput.close();
            return items;
            
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return new ItemStack[0];
        }
    }
    
    // Métodos para verificar e marcar itens do duty
    public boolean isDutyItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(dutyItemKey, PersistentDataType.BOOLEAN);
    }
    
    public String getDutyNameFromItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.has(dutyNameKey, PersistentDataType.STRING)) {
            return container.get(dutyNameKey, PersistentDataType.STRING);
        }
        return null;
    }
    
    public ItemStack markAsDutyItem(ItemStack item, String dutyName) {
        if (item == null || item.getType() == Material.AIR) return item;
        ItemStack cloned = item.clone();
        ItemMeta meta = cloned.getItemMeta();
        if (meta == null) return cloned;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(dutyItemKey, PersistentDataType.BOOLEAN, true);
        container.set(dutyNameKey, PersistentDataType.STRING, dutyName);
        cloned.setItemMeta(meta);
        return cloned;
    }
    
    public ItemStack[] markInventoryAsDuty(ItemStack[] items, String dutyName) {
        if (items == null) return null;
        ItemStack[] marked = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && items[i].getType() != Material.AIR) {
                marked[i] = markAsDutyItem(items[i], dutyName);
            }
        }
        return marked;
    }
    
    // Método para remover itens do duty de qualquer inventário de jogador
    private void removeDutyItemsFromPlayer(Player player) {
        boolean removed = false;
        PlayerInventory inv = player.getInventory();
        
        // Verificar inventário principal
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && isDutyItem(item)) {
                inv.setItem(i, null);
                removed = true;
            }
        }
        
        // Verificar armadura
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null && isDutyItem(armor[i])) {
                armor[i] = null;
                removed = true;
            }
        }
        inv.setArmorContents(armor);
        
        // Verificar extra (offhand)
        ItemStack[] extra = inv.getExtraContents();
        for (int i = 0; i < extra.length; i++) {
            if (extra[i] != null && isDutyItem(extra[i])) {
                extra[i] = null;
                removed = true;
            }
        }
        inv.setExtraContents(extra);
        
        if (removed) {
            player.sendMessage("§cDuty items have been removed from your inventory!");
            player.updateInventory();
        }
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be executed by players!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (command.getName().equalsIgnoreCase("setduty")) {
            return handleSetDutyCommand(player, args);
        } else if (command.getName().equalsIgnoreCase("unsetduty")) {
            return handleUnsetDutyCommand(player, args);
        } else if (command.getName().equalsIgnoreCase("duty")) {
            return handleDutyCommand(player, args);
        }
        return false;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }
        
        Player player = (Player) sender;
        
        if (command.getName().equalsIgnoreCase("setduty")) {
            if (args.length == 1) {
                List<String> completions = new ArrayList<>();
                completions.add("name");
                return completions.stream()
                        .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }
        } else if (command.getName().equalsIgnoreCase("unsetduty")) {
            if (args.length == 1) {
                return duties.keySet().stream()
                        .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }
        } else if (command.getName().equalsIgnoreCase("duty")) {
            if (args.length == 1) {
                return duties.keySet().stream()
                        .filter(name -> player.hasPermission("duty." + name) || player.isOp())
                        .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        
        return new ArrayList<>();
    }
    
    private boolean handleSetDutyCommand(Player player, String[] args) {
        if (!player.isOp() && !player.hasPermission("duty.admin")) {
            player.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }
        
        if (args.length != 1) {
            player.sendMessage("§cUsage: /setduty <name>");
            return true;
        }
        
        String dutyName = args[0].toLowerCase();
        
        if (duties.containsKey(dutyName)) {
            player.sendMessage("§cA duty with the name '" + dutyName + "' already exists!");
            player.sendMessage("§7Use /unsetduty " + dutyName + " to remove it first.");
            return true;
        }
        
        if (!dutyName.matches("^[a-z0-9_]+$")) {
            player.sendMessage("§cDuty name can only contain lowercase letters, numbers, and underscores!");
            return true;
        }
        
        String finalDutyName = dutyName;
        
        player.sendMessage("§7Creating duty '" + finalDutyName + "'...");
        
        CompletableFuture.runAsync(() -> {
            try {
                Duty duty = new Duty(finalDutyName);
                
                // Criar cópias e marcar como itens do duty
                ItemStack[] inventoryCopy = player.getInventory().getContents().clone();
                ItemStack[] armorCopy = player.getInventory().getArmorContents().clone();
                ItemStack[] extraCopy = player.getInventory().getExtraContents().clone();
                
                // Marcar todos os itens com NBT do duty
                ItemStack[] markedInventory = markInventoryAsDuty(inventoryCopy, finalDutyName);
                ItemStack[] markedArmor = markInventoryAsDuty(armorCopy, finalDutyName);
                ItemStack[] markedExtra = markInventoryAsDuty(extraCopy, finalDutyName);
                
                duty.setInventoryContents(markedInventory);
                duty.setArmorContents(markedArmor);
                duty.setExtraContents(markedExtra);
                
                duties.put(finalDutyName, duty);
                
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        saveDuties();
                        player.sendMessage("§aDuty '" + finalDutyName + "' created successfully!");
                        player.sendMessage("§7Required permission: duty." + finalDutyName);
                        player.sendMessage("§7Inventory saved: " + countItems(markedInventory) + " items, " + 
                                          countItems(markedArmor) + " armor pieces, " + 
                                          countItems(markedExtra) + " offhand items");
                    }
                }.runTask(DutyCraft.this);
                
            } catch (Exception e) {
                e.printStackTrace();
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendMessage("§cError creating duty: " + e.getMessage());
                    }
                }.runTask(DutyCraft.this);
            }
        });
        
        return true;
    }
    
    private int countItems(ItemStack[] items) {
        int count = 0;
        if (items != null) {
            for (ItemStack item : items) {
                if (item != null && item.getType() != Material.AIR) {
                    count++;
                }
            }
        }
        return count;
    }
    
    private boolean handleUnsetDutyCommand(Player player, String[] args) {
        if (!player.isOp() && !player.hasPermission("duty.admin")) {
            player.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }
        
        if (args.length != 1) {
            player.sendMessage("§cUsage: /unsetduty <name>");
            return true;
        }
        
        String dutyName = args[0].toLowerCase();
        
        if (!duties.containsKey(dutyName)) {
            player.sendMessage("§cDuty '" + dutyName + "' not found!");
            return true;
        }
        
        boolean inUse = false;
        for (PlayerDutyData data : playerDutyData.values()) {
            if (data.isInDuty() && dutyName.equals(data.getCurrentDuty())) {
                inUse = true;
                break;
            }
        }
        
        if (inUse) {
            player.sendMessage("§cCannot remove duty '" + dutyName + "' because it is currently in use!");
            return true;
        }
        
        String finalDutyName = dutyName;
        
        player.sendMessage("§7Removing duty '" + finalDutyName + "'...");
        
        CompletableFuture.runAsync(() -> {
            duties.remove(finalDutyName);
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    saveDuties();
                    player.sendMessage("§aDuty '" + finalDutyName + "' removed successfully!");
                }
            }.runTask(DutyCraft.this);
        });
        
        return true;
    }
    
    private boolean handleDutyCommand(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage("§cUsage: /duty <name>");
            return true;
        }
        
        String dutyName = args[0].toLowerCase();
        
        if (!duties.containsKey(dutyName)) {
            player.sendMessage("§cDuty '" + dutyName + "' not found!");
            return true;
        }
        
        if (!player.hasPermission("duty." + dutyName) && !player.isOp()) {
            player.sendMessage("§cYou don't have permission to use this duty!");
            return true;
        }
        
        Duty duty = duties.get(dutyName);
        PlayerDutyData data = playerDutyData.getOrDefault(player.getUniqueId(), new PlayerDutyData(player.getUniqueId()));
        
        if (data.isInDuty() && data.getCurrentDuty().equals(dutyName)) {
            player.sendMessage("§7Leaving duty...");
            leaveDuty(player, data);
            playerDutyData.put(player.getUniqueId(), data);
            savePlayerData();
            player.sendMessage("§aYou have left the duty '" + dutyName + "'!");
        } else if (data.isInDuty()) {
            player.sendMessage("§cYou are already in another duty! Leave it first with /duty " + data.getCurrentDuty());
        } else {
            player.sendMessage("§7Entering duty...");
            enterDuty(player, data, duty);
            playerDutyData.put(player.getUniqueId(), data);
            savePlayerData();
            player.sendMessage("§aYou have entered duty '" + duty.getName() + "'!");
            player.sendMessage("§7Duty items are protected!");
            player.sendMessage("§7- You can organize and use duty items normally");
            player.sendMessage("§7- Armor slots are locked");
            player.sendMessage("§7- Cannot drop or place duty items");
            player.sendMessage("§7- Cannot put duty items in any external container");
            player.sendMessage("§7- Can interact with other players using duty items");
        }
        
        return true;
    }
    
    private void enterDuty(Player player, PlayerDutyData data, Duty duty) {
        // Salvar inventário original
        ItemStack[] originalInventory = player.getInventory().getContents();
        ItemStack[] originalArmor = player.getInventory().getArmorContents();
        ItemStack[] originalExtra = player.getInventory().getExtraContents();
        
        ItemStack[] inventoryCopy = new ItemStack[originalInventory.length];
        ItemStack[] armorCopy = new ItemStack[originalArmor.length];
        ItemStack[] extraCopy = new ItemStack[originalExtra.length];
        
        for (int i = 0; i < originalInventory.length; i++) {
            if (originalInventory[i] != null && originalInventory[i].getType() != Material.AIR) {
                inventoryCopy[i] = originalInventory[i].clone();
            }
        }
        
        for (int i = 0; i < originalArmor.length; i++) {
            if (originalArmor[i] != null && originalArmor[i].getType() != Material.AIR) {
                armorCopy[i] = originalArmor[i].clone();
            }
        }
        
        for (int i = 0; i < originalExtra.length; i++) {
            if (originalExtra[i] != null && originalExtra[i].getType() != Material.AIR) {
                extraCopy[i] = originalExtra[i].clone();
            }
        }
        
        data.setOriginalInventory(inventoryCopy);
        data.setOriginalArmor(armorCopy);
        data.setOriginalExtraContents(extraCopy);
        data.setCurrentDuty(duty.getName());
        data.setInDuty(true);
        
        // Limpar inventário atual
        player.getInventory().clear();
        
        // Aplicar inventário do duty (já vem com NBT)
        duty.applyToInventory(player.getInventory());
        
        player.updateInventory();
    }
    
    private void leaveDuty(Player player, PlayerDutyData data) {
        // Limpar inventário atual (que tem itens do duty)
        player.getInventory().clear();
        
        // Restaurar inventário original
        if (data.getOriginalInventory() != null) {
            player.getInventory().setContents(data.getOriginalInventory());
        }
        if (data.getOriginalArmor() != null) {
            player.getInventory().setArmorContents(data.getOriginalArmor());
        }
        if (data.getOriginalExtraContents() != null) {
            player.getInventory().setExtraContents(data.getOriginalExtraContents());
        }
        
        data.setInDuty(false);
        data.setCurrentDuty(null);
        data.setOriginalInventory(null);
        data.setOriginalArmor(null);
        data.setOriginalExtraContents(null);
        
        player.updateInventory();
    }
    
    private boolean isPlayerInDuty(Player player) {
        PlayerDutyData data = playerDutyData.get(player.getUniqueId());
        return data != null && data.isInDuty();
    }
    
    private boolean isArmorSlot(int slot) {
        for (int armorSlot : ARMOR_SLOTS) {
            if (slot == armorSlot) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isExternalInventory(Inventory inventory) {
        if (inventory == null) return false;
        
        InventoryType type = inventory.getType();
        // Lista de tipos de inventário que são considerados "externos" (armazenamento)
        return type == InventoryType.CHEST ||
               type == InventoryType.BARREL ||
               type == InventoryType.SHULKER_BOX ||
               type == InventoryType.ENDER_CHEST ||
               type == InventoryType.DISPENSER ||
               type == InventoryType.DROPPER ||
               type == InventoryType.HOPPER ||
               type == InventoryType.FURNACE ||
               type == InventoryType.BLAST_FURNACE ||
               type == InventoryType.SMOKER ||
               type == InventoryType.BREWING ||
               type == InventoryType.BEACON ||
               type == InventoryType.ANVIL ||
               type == InventoryType.ENCHANTING ||
               type == InventoryType.GRINDSTONE ||
               type == InventoryType.LECTERN ||
               type == InventoryType.STONECUTTER ||
               type == InventoryType.CARTOGRAPHY ||
               type == InventoryType.LOOM ||
               type == InventoryType.MERCHANT ||
               type == InventoryType.COMPOSTER ||
               type == InventoryType.JUKEBOX ||
               type.name().contains("WORKBENCH") ||
               type == InventoryType.CRAFTING;
    }
    
    // Eventos com proteção baseada em NBT
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();
        
        if (isDutyItem(item)) {
            event.setCancelled(true);
            event.getItemDrop().remove();
            player.sendMessage("§cYou cannot drop duty items!");
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        ItemStack itemStack = item.getItemStack();
        
        if (isDutyItem(itemStack)) {
            event.setCancelled(true);
            item.remove();
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            ItemStack item = event.getItem().getItemStack();
            
            if (isDutyItem(item)) {
                event.setCancelled(true);
                event.getItem().remove();
                ((Player) event.getEntity()).sendMessage("§cYou cannot pick up duty items!");
            }
        }
    }
    
    private void startInventoryScanner() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!isPlayerInDuty(player)) {
                        removeDutyItemsFromPlayer(player);
                    }
                }
            }
        }.runTaskTimer(this, 100L, 100L);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            
            if (isPlayerInDuty(player)) {
                Inventory clickedInventory = event.getClickedInventory();
                Inventory topInventory = event.getView().getTopInventory();
                Inventory bottomInventory = event.getView().getBottomInventory();
                
                ItemStack currentItem = event.getCurrentItem();
                ItemStack cursorItem = event.getCursor();
                ItemStack hotbarItem = null;
                
                if (event.getHotbarButton() != -1) {
                    hotbarItem = player.getInventory().getItem(event.getHotbarButton());
                }
                
                boolean hasDutyItem = (currentItem != null && isDutyItem(currentItem)) ||
                                      (cursorItem != null && isDutyItem(cursorItem)) ||
                                      (hotbarItem != null && isDutyItem(hotbarItem));
                
                if (hasDutyItem) {
                    if (clickedInventory != null && isExternalInventory(clickedInventory)) {
                        event.setCancelled(true);
                        player.sendMessage("§cYou cannot interact with duty items in external containers!");
                        return;
                    }
                    
                    if (topInventory != null && isExternalInventory(topInventory) && event.isShiftClick()) {
                        event.setCancelled(true);
                        player.sendMessage("§cYou cannot move duty items to external containers!");
                        return;
                    }
                    
                    if (topInventory != null && isExternalInventory(topInventory)) {
                        if (event.getAction().name().contains("PLACE") || 
                            event.getAction().name().contains("SWAP") ||
                            event.getAction().name().contains("HOTBAR")) {
                            
                            if (clickedInventory != null && clickedInventory.equals(topInventory)) {
                                event.setCancelled(true);
                                player.sendMessage("§cYou cannot put duty items in external containers!");
                                return;
                            }
                        }
                    }
                }
                
                if (clickedInventory != null && clickedInventory.equals(bottomInventory)) {
                    if (event.getSlot() >= 0 && event.getSlot() < player.getInventory().getSize()) {
                        if (isArmorSlot(event.getSlot())) {
                            event.setCancelled(true);
                            player.sendMessage("§cYou cannot modify armor slots while in duty!");
                            return;
                        }
                    }
                }
            } else {
                ItemStack currentItem = event.getCurrentItem();
                ItemStack cursorItem = event.getCursor();
                
                if ((currentItem != null && isDutyItem(currentItem)) || 
                    (cursorItem != null && isDutyItem(cursorItem))) {
                    
                    if (currentItem != null && isDutyItem(currentItem)) {
                        event.setCurrentItem(null);
                    }
                    if (cursorItem != null && isDutyItem(cursorItem)) {
                        event.getWhoClicked().setItemOnCursor(null);
                    }
                    
                    player.sendMessage("§cDuty items have been removed from your inventory!");
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            
            if (isPlayerInDuty(player)) {
                Inventory topInventory = event.getView().getTopInventory();
                Inventory bottomInventory = event.getView().getBottomInventory();
                
                boolean hasDutyItem = false;
                for (ItemStack item : event.getNewItems().values()) {
                    if (isDutyItem(item)) {
                        hasDutyItem = true;
                        break;
                    }
                }
                
                if (hasDutyItem) {
                    for (Integer slot : event.getRawSlots()) {
                        if (slot >= 0 && slot < bottomInventory.getSize()) {
                            if (isArmorSlot(slot)) {
                                event.setCancelled(true);
                                player.sendMessage("§cYou cannot modify armor slots while in duty!");
                                return;
                            }
                        } else {
                            if (topInventory != null && isExternalInventory(topInventory)) {
                                event.setCancelled(true);
                                player.sendMessage("§cYou cannot put duty items in external containers!");
                                return;
                            }
                        }
                    }
                } else {
                    for (Integer slot : event.getRawSlots()) {
                        if (slot >= 0 && slot < bottomInventory.getSize()) {
                            if (isArmorSlot(slot)) {
                                event.setCancelled(true);
                                player.sendMessage("§cYou cannot modify armor slots while in duty!");
                                return;
                            }
                        }
                    }
                }
            } else {
                for (ItemStack item : event.getNewItems().values()) {
                    if (isDutyItem(item)) {
                        event.setCancelled(true);
                        player.sendMessage("§cDuty items cannot be moved!");
                        return;
                    }
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        if (isDutyItem(item)) {
            event.setCancelled(true);
            player.sendMessage("§cYou cannot place duty blocks!");
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        if (isPlayerInDuty(player)) {
            if (event.hasItem() && isDutyItem(event.getItem())) {
                Material itemType = event.getItem().getType();
                
                if (allowedUsableMaterials.contains(itemType)) {
                    return;
                }
                
                if (event.hasBlock()) {
                    event.setCancelled(true);
                    player.sendMessage("§cYou cannot use duty items on blocks!");
                    return;
                }
            }
        } else {
            if (event.hasItem() && isDutyItem(event.getItem())) {
                event.setCancelled(true);
                player.getInventory().setItemInMainHand(null);
                player.sendMessage("§cDuty items have been removed from your inventory!");
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        if (isPlayerInDuty(player)) {
            // Permitir interação com jogadores usando itens do duty
            if (event.getRightClicked() instanceof Player) {
                // Permite interagir com outros jogadores
                return;
            }
            
            // Para outras entidades, aplicar as regras normais
            if (item != null && isDutyItem(item)) {
                if (!allowedUsableMaterials.contains(item.getType())) {
                    event.setCancelled(true);
                    player.sendMessage("§cYou cannot use duty items on entities!");
                }
            }
        } else {
            if (item != null && isDutyItem(item)) {
                event.setCancelled(true);
                player.getInventory().setItemInMainHand(null);
                player.sendMessage("§cDuty items have been removed from your inventory!");
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getPlayerItem();
        
        if (isPlayerInDuty(player)) {
            if (item != null && isDutyItem(item)) {
                event.setCancelled(true);
                player.sendMessage("§cYou cannot use duty items on armor stands!");
            }
        } else {
            if (item != null && isDutyItem(item)) {
                event.setCancelled(true);
                player.sendMessage("§cDuty items cannot be used!");
            }
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerDutyData data = playerDutyData.get(player.getUniqueId());
        
        if (data != null && data.isInDuty()) {
            data.setInDuty(false);
            data.setCurrentDuty(null);
            player.sendMessage("§cYou have been removed from duty due to logout!");
            
            savePlayerData();
        }
        
        removeDutyItemsFromPlayer(player);
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerDutyData data = playerDutyData.get(player.getUniqueId());
        
        if (data != null && data.isInDuty()) {
            // Restaurar inventário original antes de salvar
            if (data.getOriginalInventory() != null) {
                player.getInventory().setContents(data.getOriginalInventory());
            }
            if (data.getOriginalArmor() != null) {
                player.getInventory().setArmorContents(data.getOriginalArmor());
            }
            if (data.getOriginalExtraContents() != null) {
                player.getInventory().setExtraContents(data.getOriginalExtraContents());
            }
            
            data.setInDuty(false);
            data.setCurrentDuty(null);
            
            savePlayerData();
        }
    }
    
    // Classes internas
    
    private class Duty {
        private String name;
        private ItemStack[] inventoryContents;
        private ItemStack[] armorContents;
        private ItemStack[] extraContents;
        
        public Duty(String name) {
            this.name = name;
        }
        
        public String getName() {
            return name;
        }
        
        public void setInventoryContents(ItemStack[] contents) {
            this.inventoryContents = contents;
        }
        
        public void setArmorContents(ItemStack[] contents) {
            this.armorContents = contents;
        }
        
        public void setExtraContents(ItemStack[] contents) {
            this.extraContents = contents;
        }
        
        public void saveInventory(PlayerInventory inventory) {
            this.inventoryContents = inventory.getContents().clone();
            this.armorContents = inventory.getArmorContents().clone();
            this.extraContents = inventory.getExtraContents().clone();
        }
        
        public void applyToInventory(PlayerInventory inventory) {
            if (inventoryContents != null) {
                ItemStack[] copy = new ItemStack[inventoryContents.length];
                for (int i = 0; i < inventoryContents.length; i++) {
                    if (inventoryContents[i] != null && inventoryContents[i].getType() != Material.AIR) {
                        copy[i] = inventoryContents[i].clone();
                    }
                }
                inventory.setContents(copy);
            }
            if (armorContents != null) {
                ItemStack[] copy = new ItemStack[armorContents.length];
                for (int i = 0; i < armorContents.length; i++) {
                    if (armorContents[i] != null && armorContents[i].getType() != Material.AIR) {
                        copy[i] = armorContents[i].clone();
                    }
                }
                inventory.setArmorContents(copy);
            }
            if (extraContents != null) {
                ItemStack[] copy = new ItemStack[extraContents.length];
                for (int i = 0; i < extraContents.length; i++) {
                    if (extraContents[i] != null && extraContents[i].getType() != Material.AIR) {
                        copy[i] = extraContents[i].clone();
                    }
                }
                inventory.setExtraContents(copy);
            }
        }
        
        public void saveToConfig(ConfigurationSection section) {
            section.set("inventory", serializeItemStackArray(inventoryContents));
            section.set("armor", serializeItemStackArray(armorContents));
            section.set("extra", serializeItemStackArray(extraContents));
        }
        
        public void loadFromConfig(ConfigurationSection section, DutyCraft plugin) {
            this.inventoryContents = plugin.deserializeItemStackArray(section.getString("inventory"));
            this.armorContents = plugin.deserializeItemStackArray(section.getString("armor"));
            this.extraContents = plugin.deserializeItemStackArray(section.getString("extra"));
            
            // Reaplicar NBT nos itens carregados
            if (this.inventoryContents != null) {
                for (int i = 0; i < this.inventoryContents.length; i++) {
                    if (this.inventoryContents[i] != null && this.inventoryContents[i].getType() != Material.AIR) {
                        this.inventoryContents[i] = plugin.markAsDutyItem(this.inventoryContents[i], name);
                    }
                }
            }
            if (this.armorContents != null) {
                for (int i = 0; i < this.armorContents.length; i++) {
                    if (this.armorContents[i] != null && this.armorContents[i].getType() != Material.AIR) {
                        this.armorContents[i] = plugin.markAsDutyItem(this.armorContents[i], name);
                    }
                }
            }
            if (this.extraContents != null) {
                for (int i = 0; i < this.extraContents.length; i++) {
                    if (this.extraContents[i] != null && this.extraContents[i].getType() != Material.AIR) {
                        this.extraContents[i] = plugin.markAsDutyItem(this.extraContents[i], name);
                    }
                }
            }
        }
    }
    
    private class PlayerDutyData {
        private UUID playerId;
        private boolean inDuty;
        private String currentDuty;
        private ItemStack[] originalInventory;
        private ItemStack[] originalArmor;
        private ItemStack[] originalExtraContents;
        
        public PlayerDutyData(UUID playerId) {
            this.playerId = playerId;
            this.inDuty = false;
        }
        
        public boolean isInDuty() {
            return inDuty;
        }
        
        public void setInDuty(boolean inDuty) {
            this.inDuty = inDuty;
        }
        
        public String getCurrentDuty() {
            return currentDuty;
        }
        
        public void setCurrentDuty(String currentDuty) {
            this.currentDuty = currentDuty;
        }
        
        public ItemStack[] getOriginalInventory() {
            return originalInventory;
        }
        
        public void setOriginalInventory(ItemStack[] originalInventory) {
            this.originalInventory = originalInventory;
        }
        
        public ItemStack[] getOriginalArmor() {
            return originalArmor;
        }
        
        public void setOriginalArmor(ItemStack[] originalArmor) {
            this.originalArmor = originalArmor;
        }
        
        public ItemStack[] getOriginalExtraContents() {
            return originalExtraContents;
        }
        
        public void setOriginalExtraContents(ItemStack[] originalExtraContents) {
            this.originalExtraContents = originalExtraContents;
        }
        
        public void saveToConfig(ConfigurationSection section) {
            section.set("inDuty", inDuty);
            section.set("currentDuty", currentDuty);
            
            if (originalInventory != null) {
                section.set("originalInventory", serializeItemStackArray(originalInventory));
            }
            if (originalArmor != null) {
                section.set("originalArmor", serializeItemStackArray(originalArmor));
            }
            if (originalExtraContents != null) {
                section.set("originalExtra", serializeItemStackArray(originalExtraContents));
            }
        }
        
        public void loadFromConfig(ConfigurationSection section, DutyCraft plugin) {
            this.inDuty = section.getBoolean("inDuty", false);
            this.currentDuty = section.getString("currentDuty");
            this.originalInventory = plugin.deserializeItemStackArray(section.getString("originalInventory"));
            this.originalArmor = plugin.deserializeItemStackArray(section.getString("originalArmor"));
            this.originalExtraContents = plugin.deserializeItemStackArray(section.getString("originalExtra"));
        }
    }
}
