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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
    // Slot da mão secundária (offhand)
    private final int OFFHAND_SLOT = 40;
    
    @Override
    public void onEnable() {
        // Inicializar NBT keys
        dutyItemKey = new NamespacedKey(this, "duty_item");
        dutyNameKey = new NamespacedKey(this, "duty_name");
        
        // Criar arquivos de configuração
        createFiles();
        
        // Registrar tab completer
        getCommand("setduty").setTabCompleter(this);
        getCommand("unsetduty").setTabCompleter(this);
        getCommand("duty").setTabCompleter(this);
        
        // Carregar dados de forma assíncrona
        CompletableFuture.runAsync(() -> {
            loadDuties();
            loadPlayerData();
        }).thenRun(() -> {
            // Registrar eventos após carregar dados
            getServer().getPluginManager().registerEvents(this, this);
            
            // Configurar materiais permitidos
            setupAllowedMaterials();
            
            getLogger().info("DutyCraft has been enabled successfully!");
            getLogger().info("Loaded " + duties.size() + " duties!");
        }).exceptionally(throwable -> {
            getLogger().severe("Error loading data: " + throwable.getMessage());
            return null;
        });
    }
    
    @Override
    public void onDisable() {
        // Salvar dados de forma síncrona ao desabilitar
        savePlayerData();
        saveDuties();
        
        getLogger().info("DutyCraft has been disabled!");
    }
    
    private void createFiles() {
        // Arquivo de duties
        dutiesFile = new File(getDataFolder(), "duties.yml");
        if (!dutiesFile.exists()) {
            dutiesFile.getParentFile().mkdirs();
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
            playerDataFile.getParentFile().mkdirs();
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
                        duty.loadFromConfig(section.getConfigurationSection(key));
                        duties.put(key, duty);
                        getLogger().info("Loaded duty: " + key);
                    } catch (Exception e) {
                        getLogger().warning("Error loading duty '" + key + "': " + e.getMessage());
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
                        data.loadFromConfig(section.getConfigurationSection(key));
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
    
    // Métodos para verificar e marcar itens do duty
    private boolean isDutyItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(dutyItemKey, PersistentDataType.BOOLEAN);
    }
    
    private String getDutyNameFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.has(dutyNameKey, PersistentDataType.STRING)) {
            return container.get(dutyNameKey, PersistentDataType.STRING);
        }
        return null;
    }
    
    private ItemStack markAsDutyItem(ItemStack item, String dutyName) {
        if (item == null || item.getType() == Material.AIR) return item;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(dutyItemKey, PersistentDataType.BOOLEAN, true);
        container.set(dutyNameKey, PersistentDataType.STRING, dutyName);
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack[] markInventoryAsDuty(ItemStack[] items, String dutyName) {
        if (items == null) return null;
        ItemStack[] marked = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && items[i].getType() != Material.AIR) {
                marked[i] = markAsDutyItem(items[i].clone(), dutyName);
            }
        }
        return marked;
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
        
        player.sendMessage("§7Creating duty '" + finalDutyName + "'... This may take a few seconds.");
        
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
                saveDuties();
                
                // Criar cópias finais para usar no BukkitRunnable
                final ItemStack[] finalInventory = markedInventory;
                final ItemStack[] finalArmor = markedArmor;
                final ItemStack[] finalExtra = markedExtra;
                
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendMessage("§aDuty '" + finalDutyName + "' created successfully!");
                        player.sendMessage("§7Required permission: duty." + finalDutyName);
                        player.sendMessage("§7Inventory saved: " + countItems(finalInventory) + " items, " + 
                                          countItems(finalArmor) + " armor pieces, " + 
                                          countItems(finalExtra) + " offhand items");
                    }
                }.runTask(this);
                
            } catch (Exception e) {
                e.printStackTrace();
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendMessage("§cError creating duty: " + e.getMessage());
                    }
                }.runTask(this);
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
            saveDuties();
        }).thenRun(() -> {
            new BukkitRunnable() {
                @Override
                public void run() {
                    player.sendMessage("§aDuty '" + finalDutyName + "' removed successfully!");
                }
            }.runTask(this);
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
            
            CompletableFuture.runAsync(() -> {
                leaveDuty(player, data);
            }).thenRun(() -> {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        playerDutyData.put(player.getUniqueId(), data);
                        savePlayerData();
                        player.sendMessage("§aYou have left the duty '" + dutyName + "'!");
                    }
                }.runTask(this);
            });
        } else if (data.isInDuty()) {
            player.sendMessage("§cYou are already in another duty! Leave it first with /duty " + data.getCurrentDuty());
        } else {
            player.sendMessage("§7Entering duty...");
            
            CompletableFuture.runAsync(() -> {
                enterDuty(player, data, duty);
            }).thenRun(() -> {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        playerDutyData.put(player.getUniqueId(), data);
                        savePlayerData();
                        player.sendMessage("§aYou have entered duty '" + duty.getName() + "'!");
                        player.sendMessage("§7Duty items are protected!");
                        player.sendMessage("§7- You can organize and use duty items normally");
                        player.sendMessage("§7- Armor slots are locked");
                        player.sendMessage("§7- Cannot drop or place duty items");
                        player.sendMessage("§7- Cannot put duty items in any external container");
                    }
                }.runTask(this);
            });
        }
        
        return true;
    }
    
    private void enterDuty(Player player, PlayerDutyData data, Duty duty) {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Salvar inventário original
                ItemStack[] originalInventory = player.getInventory().getContents();
                ItemStack[] originalArmor = player.getInventory().getArmorContents();
                ItemStack[] originalExtra = player.getInventory().getExtraContents();
                
                ItemStack[] inventoryCopy = new ItemStack[originalInventory.length];
                ItemStack[] armorCopy = new ItemStack[originalArmor.length];
                ItemStack[] extraCopy = new ItemStack[originalExtra.length];
                
                for (int i = 0; i < originalInventory.length; i++) {
                    if (originalInventory[i] != null) {
                        inventoryCopy[i] = originalInventory[i].clone();
                    }
                }
                
                for (int i = 0; i < originalArmor.length; i++) {
                    if (originalArmor[i] != null) {
                        armorCopy[i] = originalArmor[i].clone();
                    }
                }
                
                for (int i = 0; i < originalExtra.length; i++) {
                    if (originalExtra[i] != null) {
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
        }.runTask(this);
    }
    
    private void leaveDuty(Player player, PlayerDutyData data) {
        new BukkitRunnable() {
            @Override
            public void run() {
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
        }.runTask(this);
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
               type.name().contains("WORKBENCH") || // Para versões mais antigas
               type == InventoryType.CRAFTING; // Mesa de trabalho
    }
    
    // Eventos com proteção baseada em NBT
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();
        
        if (isPlayerInDuty(player) && isDutyItem(item)) {
            event.setCancelled(true);
            player.sendMessage("§cYou cannot drop duty items!");
        }
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
                
                // Verificar se é uma ação de hotbar (números 1-9 ou F)
                if (event.getHotbarButton() != -1) {
                    hotbarItem = player.getInventory().getItem(event.getHotbarButton());
                }
                
                // Verificar se qualquer item envolvido é do duty
                boolean hasDutyItem = (currentItem != null && isDutyItem(currentItem)) ||
                                      (cursorItem != null && isDutyItem(cursorItem)) ||
                                      (hotbarItem != null && isDutyItem(hotbarItem));
                
                if (hasDutyItem) {
                    // Verificar se o inventário clicado é externo
                    if (clickedInventory != null && isExternalInventory(clickedInventory)) {
                        // Bloquear qualquer interação com itens do duty em inventários externos
                        event.setCancelled(true);
                        player.sendMessage("§cYou cannot interact with duty items in external containers!");
                        return;
                    }
                    
                    // Verificar se o inventário superior é externo (para casos de shift+click)
                    if (topInventory != null && isExternalInventory(topInventory) && event.isShiftClick()) {
                        // Shift-click em item do duty com inventário externo aberto
                        event.setCancelled(true);
                        player.sendMessage("§cYou cannot move duty items to external containers!");
                        return;
                    }
                    
                    // Verificar se está movendo para o inventário superior (externo)
                    if (topInventory != null && isExternalInventory(topInventory)) {
                        if (event.getAction().name().contains("PLACE") || 
                            event.getAction().name().contains("SWAP") ||
                            event.getAction().name().contains("HOTBAR")) {
                            
                            // Se o slot clicado for do inventário superior, bloquear
                            if (clickedInventory != null && clickedInventory.equals(topInventory)) {
                                event.setCancelled(true);
                                player.sendMessage("§cYou cannot put duty items in external containers!");
                                return;
                            }
                        }
                    }
                }
                
                // Verificar slots de armadura - SEMPRE BLOQUEAR interação com slots de armadura
                if (clickedInventory != null && clickedInventory.equals(bottomInventory)) {
                    if (event.getSlot() >= 0 && event.getSlot() < player.getInventory().getSize()) {
                        if (isArmorSlot(event.getSlot())) {
                            event.setCancelled(true);
                            player.sendMessage("§cYou cannot modify armor slots while in duty!");
                            return;
                        }
                    }
                }
                
                // Para todos os outros casos (movimentação dentro do próprio inventário do jogador),
                // permitir movimento normal de itens
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
                
                // Verificar se algum item sendo arrastado é do duty
                boolean hasDutyItem = false;
                for (ItemStack item : event.getNewItems().values()) {
                    if (isDutyItem(item)) {
                        hasDutyItem = true;
                        break;
                    }
                }
                
                if (hasDutyItem) {
                    // Verificar se algum slot de destino é de inventário externo
                    for (Integer slot : event.getRawSlots()) {
                        // Determinar a qual inventário o slot pertence
                        if (slot >= 0 && slot < bottomInventory.getSize()) {
                            // Slot do jogador - verificar se é armor
                            if (isArmorSlot(slot)) {
                                event.setCancelled(true);
                                player.sendMessage("§cYou cannot modify armor slots while in duty!");
                                return;
                            }
                        } else {
                            // Slot possivelmente externo
                            if (topInventory != null && isExternalInventory(topInventory)) {
                                event.setCancelled(true);
                                player.sendMessage("§cYou cannot put duty items in external containers!");
                                return;
                            }
                        }
                    }
                } else {
                    // Itens normais - verificar apenas slots de armadura
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
                
                // Para itens normais em slots normais, permitir drag
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        if (isPlayerInDuty(player) && isDutyItem(item)) {
            event.setCancelled(true);
            player.sendMessage("§cYou cannot place duty blocks!");
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        if (isPlayerInDuty(player)) {
            // Verificar se está interagindo com bloco usando item do duty
            if (event.hasItem() && isDutyItem(event.getItem())) {
                Material itemType = event.getItem().getType();
                
                // Permitir usar itens permitidos mesmo sendo do duty (comer, beber, etc.)
                if (allowedUsableMaterials.contains(itemType)) {
                    return; // Permitir uso
                }
                
                // Bloquear outras interações com itens do duty (como clicar em blocos com ferramentas)
                if (event.hasBlock()) {
                    event.setCancelled(true);
                    player.sendMessage("§cYou cannot use duty items on blocks!");
                    return;
                }
            }
            
            // Para itens normais, permitir interações normais
            // Interação com blocos usando a mão vazia ou itens normais - permitir
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        
        if (isPlayerInDuty(player)) {
            ItemStack item = player.getInventory().getItemInMainHand();
            
            // Se estiver usando item do duty em entidade, bloquear (a menos que seja permitido)
            if (item != null && isDutyItem(item)) {
                if (!allowedUsableMaterials.contains(item.getType())) {
                    event.setCancelled(true);
                    player.sendMessage("§cYou cannot use duty items on entities!");
                }
            }
            // Para itens normais, permitir interagir com entidades
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();
        
        if (isPlayerInDuty(player)) {
            ItemStack item = event.getPlayerItem();
            
            // Se estiver usando item do duty em armor stand, bloquear
            if (item != null && isDutyItem(item)) {
                event.setCancelled(true);
                player.sendMessage("§cYou cannot use duty items on armor stands!");
            }
            // Para itens normais, permitir manipular armor stands
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerDutyData data = playerDutyData.get(player.getUniqueId());
        
        if (data != null && data.isInDuty()) {
            // Se o jogador estava em duty quando saiu, remover do duty por segurança
            data.setInDuty(false);
            data.setCurrentDuty(null);
            player.sendMessage("§cYou have been removed from duty due to logout!");
            
            // Salvar alteração
            CompletableFuture.runAsync(() -> {
                savePlayerData();
            });
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerDutyData data = playerDutyData.get(player.getUniqueId());
        
        if (data != null) {
            if (data.isInDuty()) {
                // Restaurar inventário antes de salvar
                if (data.getOriginalInventory() != null) {
                    player.getInventory().setContents(data.getOriginalInventory());
                }
                if (data.getOriginalArmor() != null) {
                    player.getInventory().setArmorContents(data.getOriginalArmor());
                }
                if (data.getOriginalExtraContents() != null) {
                    player.getInventory().setExtraContents(data.getOriginalExtraContents());
                }
                
                // Marcar como não está mais em duty
                data.setInDuty(false);
                data.setCurrentDuty(null);
            }
            
            CompletableFuture.runAsync(() -> {
                savePlayerData();
            });
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
                    if (inventoryContents[i] != null) {
                        copy[i] = inventoryContents[i].clone();
                    }
                }
                inventory.setContents(copy);
            }
            if (armorContents != null) {
                ItemStack[] copy = new ItemStack[armorContents.length];
                for (int i = 0; i < armorContents.length; i++) {
                    if (armorContents[i] != null) {
                        copy[i] = armorContents[i].clone();
                    }
                }
                inventory.setArmorContents(copy);
            }
            if (extraContents != null) {
                ItemStack[] copy = new ItemStack[extraContents.length];
                for (int i = 0; i < extraContents.length; i++) {
                    if (extraContents[i] != null) {
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
        
        public void loadFromConfig(ConfigurationSection section) {
            this.inventoryContents = deserializeItemStackArray(section.getString("inventory"));
            this.armorContents = deserializeItemStackArray(section.getString("armor"));
            this.extraContents = deserializeItemStackArray(section.getString("extra"));
            
            // Reaplicar NBT nos itens carregados
            if (this.inventoryContents != null) {
                for (int i = 0; i < this.inventoryContents.length; i++) {
                    if (this.inventoryContents[i] != null && this.inventoryContents[i].getType() != Material.AIR) {
                        this.inventoryContents[i] = markAsDutyItem(this.inventoryContents[i], name);
                    }
                }
            }
            if (this.armorContents != null) {
                for (int i = 0; i < this.armorContents.length; i++) {
                    if (this.armorContents[i] != null && this.armorContents[i].getType() != Material.AIR) {
                        this.armorContents[i] = markAsDutyItem(this.armorContents[i], name);
                    }
                }
            }
            if (this.extraContents != null) {
                for (int i = 0; i < this.extraContents.length; i++) {
                    if (this.extraContents[i] != null && this.extraContents[i].getType() != Material.AIR) {
                        this.extraContents[i] = markAsDutyItem(this.extraContents[i], name);
                    }
                }
            }
        }
        
        private String serializeItemStackArray(ItemStack[] items) {
            if (items == null) return "";
            
            StringBuilder sb = new StringBuilder();
            for (ItemStack item : items) {
                if (item != null && item.getType() != Material.AIR) {
                    sb.append(item.getType().toString()).append(":").append(item.getAmount()).append(";");
                } else {
                    sb.append("AIR:0;");
                }
            }
            return sb.toString();
        }
        
        private ItemStack[] deserializeItemStackArray(String data) {
            if (data == null || data.isEmpty()) return new ItemStack[0];
            
            String[] parts = data.split(";");
            ItemStack[] items = new ItemStack[parts.length];
            
            for (int i = 0; i < parts.length; i++) {
                String[] itemData = parts[i].split(":");
                try {
                    Material material = Material.getMaterial(itemData[0]);
                    int amount = Integer.parseInt(itemData[1]);
                    
                    if (material != null && material != Material.AIR && amount > 0) {
                        items[i] = new ItemStack(material, amount);
                    } else {
                        items[i] = new ItemStack(Material.AIR);
                    }
                } catch (Exception e) {
                    items[i] = new ItemStack(Material.AIR);
                }
            }
            
            return items;
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
        
        public void loadFromConfig(ConfigurationSection section) {
            this.inDuty = section.getBoolean("inDuty", false);
            this.currentDuty = section.getString("currentDuty");
            this.originalInventory = deserializeItemStackArray(section.getString("originalInventory"));
            this.originalArmor = deserializeItemStackArray(section.getString("originalArmor"));
            this.originalExtraContents = deserializeItemStackArray(section.getString("originalExtra"));
        }
        
        private String serializeItemStackArray(ItemStack[] items) {
            if (items == null) return "";
            
            StringBuilder sb = new StringBuilder();
            for (ItemStack item : items) {
                if (item != null && item.getType() != Material.AIR) {
                    sb.append(item.getType().toString()).append(":").append(item.getAmount()).append(";");
                } else {
                    sb.append("AIR:0;");
                }
            }
            return sb.toString();
        }
        
        private ItemStack[] deserializeItemStackArray(String data) {
            if (data == null || data.isEmpty()) return new ItemStack[0];
            
            String[] parts = data.split(";");
            ItemStack[] items = new ItemStack[parts.length];
            
            for (int i = 0; i < parts.length; i++) {
                String[] itemData = parts[i].split(":");
                try {
                    Material material = Material.getMaterial(itemData[0]);
                    int amount = Integer.parseInt(itemData[1]);
                    
                    if (material != null && material != Material.AIR && amount > 0) {
                        items[i] = new ItemStack(material, amount);
                    } else {
                        items[i] = new ItemStack(Material.AIR);
                    }
                } catch (Exception e) {
                    items[i] = new ItemStack(Material.AIR);
                }
            }
            
            return items;
        }
    }
}
