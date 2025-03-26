// Original source
package org.furranystudio.thefakeplayer.Entity;

// org imports
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.properties.Property;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.ItemLike;
import org.furranystudio.thefakeplayer.Entity.Renderer.FakePlayerRenderer;
import org.furranystudio.thefakeplayer.Thefakeplayer;
import org.jetbrains.annotations.Nullable;

// mc imports
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

// java imports
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class FakePlayerEntity extends Animal implements NeutralMob, InventoryCarrier {

    // Attributs - Variables
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    private int remainingPersistentAngerTime;
    @javax.annotation.Nullable
    private UUID persistentAngerTarget;
    private static int FAKEPLAYER_INVENTORY_SIZE = 36;
    private final SimpleContainer inventory = new SimpleContainer(FAKEPLAYER_INVENTORY_SIZE);

    // Attributs - Entity Info
    private static String ENTITY_NAME = "fake_player_entity";
    private static String entityUUID;
    private ResourceLocation customSkin;

    // Constructeurs
    public FakePlayerEntity(EntityType<? extends Animal> entityType, Level world) {
        super(entityType, world);
    }

    public FakePlayerEntity(EntityType<? extends Animal> entityType, Level world, String playerName)
    {
        super(entityType, world);
        UpdateEntityProfile(playerName);
        this.setCanPickUpLoot(true);
    }

    public FakePlayerEntity(EntityType<? extends Animal> entityType, Level world, double x, double y, double z) {
        super(entityType, world);
        this.setPos(x, y, z);
    }

    public FakePlayerEntity(EntityType<? extends Animal> entityType, Level world, double x, double y, double z, String playerName) {
        super(entityType, world);
        this.setPos(x, y, z);
        UpdateEntityProfile(playerName);
    }

    public FakePlayerEntity(EntityType<? extends Animal> entityType, Level world, BlockPos pos) {
        super(entityType, world);
        this.setPos(pos.getX(), pos.getY(), pos.getZ());
    }

    public FakePlayerEntity(EntityType<? extends Animal> entityType, Level world, BlockPos pos, String playerName) {
        super(entityType, world);
        this.setPos(pos.getX(), pos.getY(), pos.getZ());
        UpdateEntityProfile(playerName);
    }

    public FakePlayerEntity(Level world) {
        super(ModEntities.FAKE_PLAYER_ENTITY.get(), world);
    }

    public FakePlayerEntity(Level world, String playerName) {
        super(ModEntities.FAKE_PLAYER_ENTITY.get(), world);
        UpdateEntityProfile(playerName);
    }

    public FakePlayerEntity(Level world, double x, double y, double z) {
        super(ModEntities.FAKE_PLAYER_ENTITY.get(), world);
        this.setPos(x, y, z);
    }

    public FakePlayerEntity(Level world, double x, double y, double z, String playerName) {
        super(ModEntities.FAKE_PLAYER_ENTITY.get(), world);
        this.setPos(x, y, z);
        UpdateEntityProfile(playerName);
    }

    public FakePlayerEntity(Level world, BlockPos pos) {
        super(ModEntities.FAKE_PLAYER_ENTITY.get(), world);
        this.setPos(pos.getX(), pos.getY(), pos.getZ());
    }

    public FakePlayerEntity(Level world, BlockPos pos, String playerName) {
        super(ModEntities.FAKE_PLAYER_ENTITY.get(), world);
        this.setPos(pos.getX(), pos.getY(), pos.getZ());
        UpdateEntityProfile(playerName);
    }

    // Methods - Entity for FakePlayerEntity
    @Override
    protected void registerGoals() {

        // Add goals to the entity
        this.goalSelector.addGoal(0, new FloatGoal(this)); // Permet de flotter dans l'eau
        this.goalSelector.addGoal(1, new RandomStrollGoal(this, 1.0D)); // Permet de se déplacer aléatoirement
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F)); // Permet de regarder le joueur
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true)); // Permet d'attaquer le joueur
        this.goalSelector.addGoal(3, new BreakDoorGoal(this, (HARD) -> {
            return true;
        })); // Permet de casser les portes
        this.goalSelector.addGoal(2, new OpenDoorGoal(this, true)); // Permet d'ouvrir les portes
        this.goalSelector.addGoal(4, new JumpGoal() {
            @Override
            public boolean canUse() {
                return true;
            }
        });
        this.goalSelector.addGoal(5, new PanicGoal(this, 1.25D)); // Permet de paniquer
        this.goalSelector.addGoal(2, new EatBlockGoal(this)); // Permet de manger des blocs
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector
                .addGoal(
                        2,
                        new NearestAttackableTargetGoal<>(
                                this, Mob.class, 3, false, false, (p_28879_, p_363579_) -> p_28879_ instanceof LivingEntity
                        )
                );
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Player.class, true));
        this.targetSelector.addGoal(6, new ResetUniversalAngerTargetGoal<>(this, false));
        this.goalSelector.addGoal(3, new MoveThroughVillageGoal(this, 1.0, true, 4, this::canBreakDoors)); // Permet de se déplacer dans le village

        this.setCanPickUpLoot(true);

        // Update the entity's profile
        UpdateEntityProfile();
    }

    // Update the entity's profile
    private void UpdateEntityProfile()
    {
        // Change the entity's name
        if (hasInternetConnection()) {
            try {
                String playerName = getRandomName();
                String playerUUID = getUUIDFromName(playerName);
                String skinURL = (playerUUID != null) ? getSkinURL(playerUUID) : null;

                if (playerUUID != null && skinURL != null) {
                    this.setCustomName(Component.literal(playerName));
                    setEntityName(playerName);
                    setEntityUUID(playerUUID);
                    applySkin(playerName, playerUUID);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            this.setCustomName(Component.literal("Steve"));
            getRenderer().updateTexture(ResourceLocation.fromNamespaceAndPath(Thefakeplayer.MODID, "textures/entities/basefakeplayer.png"));
        }
    }

    private void UpdateEntityProfile(String playerName)
    {
        // Change the entity's name
        if (hasInternetConnection()) {
            try {
                String playerUUID = getUUIDFromName(playerName);
                String skinURL = (playerUUID != null) ? getSkinURL(playerUUID) : null;

                if (playerUUID != null && skinURL != null) {
                    this.setCustomName(Component.literal(playerName));
                    setEntityName(playerName);
                    setEntityUUID(playerUUID);
                    applySkin(playerName, playerUUID);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            this.setCustomName(Component.literal("Steve"));
            getRenderer().updateTexture(ResourceLocation.fromNamespaceAndPath(Thefakeplayer.MODID, "textures/entities/basefakeplayer.png"));
        }
    }

    // Create the entity's attributes
    public static AttributeSupplier.Builder createFakePlayerAttributes() {
        return Mob.createMobAttributes()
                // Same stats as a player
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0D)
                .add(Attributes.ATTACK_SPEED, 4.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D)
                .add(Attributes.BLOCK_BREAK_SPEED, 5.0D)
                .add(Attributes.MINING_EFFICIENCY, 5.0D)
                .add(Attributes.BLOCK_INTERACTION_RANGE, 6.0D);

    }

    // Get Custom Skin Location
    public ResourceLocation getCustomSkin() {
        return customSkin != null ? customSkin : ResourceLocation.fromNamespaceAndPath(Thefakeplayer.MODID,"textures/entities/basefakeplayer.png");  // Skin par défaut si aucun personnalisé
    }

    // Set Custom Skin Location
    public void setCustomSkin(ResourceLocation skin) {
        this.customSkin = skin;

        ResourceLocation skinLocation = ResourceLocation.fromNamespaceAndPath(Thefakeplayer.MODID, "config/thefakeplayer/" + skin.getPath());

        File skinFile = new File(skinLocation.getPath());

        // Update the entity's texture
        FakePlayerRenderer.updateTextureFromFile(skinFile);

        // Update the current texture of the entity
        this.refreshDimensions();
    }

    // Get Entity Name
    public static String getEntityName() {
        return ENTITY_NAME;
    }

    // Set Entity Name
    public static void setEntityName(String entityName) {
        ENTITY_NAME = entityName;
    }

    // Get Entity UUID
    public static String getEntityUUID() {
        return entityUUID;
    }

    // Set Entity UUID
    public static void setEntityUUID(String newentityUUID) {
        entityUUID = newentityUUID;
    }

    // CUSTOM USERNAME AND SKIN
    private static final String[] NAMES = {
            "Notch", "Herobrine", "Dream", "Technoblade",
            "Steve", "Alex", "CaptainSparklez", "PrestonPlayz",
            "Skeppy", "BadBoyHalo", "GeorgeNotFound", "DreamWasTaken",
            "TommyInnit", "WilburSoot", "Tubbo", "Quackity",
            "KarlJacobs", "Sapnap", "Nihachu", "CorpseHusband",
            "Sykkuno", "Valkyrae", "Pokimane", "DisguisedToast",
            "Ludwig", "xQc", "Mizkif", "Trainwreckstv",
            "Asmongold", "Shroud", "Ninja", "DrLupo",
            "TimTheTatman", "CourageJD", "NickMercs", "Tfue",
            "Bugha", "Clix", "Mongraal", "Benjyfishy",
            "MrBeast", "LazarBeam", "Lachlan", "Muselk",
            "Fresh", "Lufu", "TheFantasio974", "BobLennon",
            "FuzeIII", "Furious_Jumper", "Frigiel", "Aurelien_Sama",
            "aypierre", "Aybierre", "AyR0b0t", "BillSilverlight",
            "thoyy", "R3li3nt", "EdoRock", "JuJoue",
            "Rayenryuu", "ShoukaSeikyo", "tabernak", "DavLec",
            "Wotan83", "Myla", "dunaway", "MrMLDEG",
            "Siphano", "KeyOps", "Clintwood", "Daidalos",
            "Zedh74", "Alkasym", "AlpZz", "Bahason",
            "Goldawn", "MC_Ika", "JimmyBoyyy", "kisukeisflo",
            "LetoVII", "LeTsaudric", "Magicknup", "Mayukow",
            "nems", "Orann", "RedToxx", "The_Boune",
            "Steelorse", "TheGuill84", "tungstene74", "Vartac",
            "Zakarum92", "Zanzag", "Zeptuna", "AEkeep",
            "Anaey", "Ariux", "Boitameu", "DaftGuy",
            "Guep", "Lerelo", "Lvcyd", "Mayleenor",
            "Nimbus", "Nagatow12", "tadjin", "the_smitties",
            "Louseron", "Max", "KoldGeneration", "Roi_Louis",
            "Zephirr", "ninjaxx", "Hctuan", "THEODORT",
            "itsBl0om", "L4rsen", "zafeel", "SamEzTwitch",
            "SteveLeBoss", "GobelinFlingueur", "Posuu_", "_RAFFALE_",
            "EmmaGraziano", "Chris_jeux", "FoxieFern", "Masturlute",
            "Michoucroute", "Maxime66410", "mimimama", "White_fri2z"
    };

    // Get random name
    public static String getRandomName() {
        return NAMES[new Random().nextInt(NAMES.length)];
    }

    // Get UUID from player name
    public static String getUUIDFromName(String playerName) throws IOException {
        URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + playerName);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        if (conn.getResponseCode() != 200) {
            return null;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder responseBuilder = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            responseBuilder.append(line);
        }

        reader.close();

        // Convert to string
        String response = responseBuilder.toString();

        // Convert to JSON
        JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();

        // Get the UUID
        if (jsonObject.has("id")) {
            return jsonObject.get("id").getAsString();
        }

        return null;
    }

    // Get skin URL from UUID
    public static String getSkinURL(String uuid) throws IOException {
        URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        if (conn.getResponseCode() != 200) {
            return null;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder responseBuilder = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            responseBuilder.append(line);
        }

        reader.close();

        // Convert to string
        String response = responseBuilder.toString();

        // Convert to JSON
        JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();

        // Get the properties
        if (jsonObject.has("properties")) {
            JsonArray properties = jsonObject.getAsJsonArray("properties");
            for (JsonElement element : properties) {
                JsonObject property = element.getAsJsonObject();
                if (property.get("name").getAsString().equals("textures")) {
                    String base64Encoded = property.get("value").getAsString();
                    String decoded = new String(Base64.getDecoder().decode(base64Encoded));
                    JsonObject textureData = JsonParser.parseString(decoded).getAsJsonObject();
                    return textureData.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
                }
            }
        }

        return null;
    }

    // Download and save skin
    public static ResourceLocation downloadAndSaveSkin(String skinURL, String playerName) throws IOException {
        // Avant de télécharger le skin, vérifier si le dossier skins existe
        File skinsDir = new File("config/"+Thefakeplayer.MODID+"/skins");
        if (!skinsDir.exists()) {
            skinsDir.mkdirs();
        }

        String safePlayerName = playerName.toLowerCase().replaceAll("[^a-z0-9_-]", "_");

        // Vérifier si le skin existe déjà
        File skinFile = new File(skinsDir, safePlayerName + ".png");
        if (skinFile.exists()) {
            return ResourceLocation.fromNamespaceAndPath(Thefakeplayer.MODID, "skins/" + safePlayerName + ".png");
        }

        BufferedImage skinImage = ImageIO.read(new URL(skinURL));  // Télécharger l'image
        // Convertir le nom du fichier en minuscules et remplacer les espaces par des underscores (_)
        File newSkinFile = new File("config/" +Thefakeplayer.MODID + "/skins/" + safePlayerName + ".png");  // Créer un fichier pour le skin
        newSkinFile.getParentFile().mkdirs();  // Créer les répertoires si nécessaire
        ImageIO.write(skinImage, "png", newSkinFile);  // Sauvegarder l'image sur le disque

        // Retourner le chemin du skin dans un format valide pour ResourceLocation
        return ResourceLocation.fromNamespaceAndPath(Thefakeplayer.MODID, "skins/" + safePlayerName + ".png");
    }

    // Download and save skin and apply it to the entity
    public void applySkin(String playerName, String uuid) {
        try {
            String skinURL = getSkinURL(uuid);  // Récupérer l'URL du skin à partir de l'UUID
            if (skinURL != null) {
                // Télécharger le skin et l'enregistrer localement
                setCustomSkin(downloadAndSaveSkin(skinURL, playerName));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Check if the entity has internet connection
    public static boolean hasInternetConnection() {
        try {
            URL url = new URL("https://www.google.com");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(3000); // Timeout de 3 secondes
            connection.connect();
            return (connection.getResponseCode() == 200);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    protected void actuallyHurt(ServerLevel p_364204_, DamageSource p_328294_, float p_327706_) {
        super.actuallyHurt(p_364204_, p_328294_, p_327706_);

        Entity attacker = p_328294_.getEntity();
        if (attacker instanceof LivingEntity) {
            this.setTarget((LivingEntity) p_328294_.getEntity());
        }

        // If the entity is dead or dying, remove it
        if (this.isDeadOrDying()) {
            // Give effect slowness and blindness

            if(attacker instanceof LivingEntity) {
                ((LivingEntity) attacker).addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 10));
                ((LivingEntity) attacker).addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 3));
            }

            dropCustomDeathLoot(p_364204_, p_328294_, true);

            this.remove(RemovalReason.DISCARDED);
        }
    }

    public static boolean canSpawn(EntityType<? extends FakePlayerEntity> p_223365_, LevelAccessor p_223366_, EntitySpawnReason p_223367_, BlockPos p_223368_, RandomSource p_223369_) {
        return Animal.checkAnimalSpawnRules(p_223365_, p_223366_, p_223367_, p_223368_, p_223369_);
    }

    public boolean canBreakDoors() {
        return true;
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel p_146743_, AgeableMob p_146744_) {
        return null;
    }

    @Override
    public boolean isFood(ItemStack p_27600_) {
        return false;
    }

    @Override
    public int getRemainingPersistentAngerTime() {
        return this.remainingPersistentAngerTime;
    }

    @Override
    public void setRemainingPersistentAngerTime(int p_21673_) {
        this.remainingPersistentAngerTime = p_21673_;
    }

    @Override
    public @Nullable UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID p_21672_) {
        this.persistentAngerTarget = p_21672_;
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(PERSISTENT_ANGER_TIME.sample(this.random));
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel p_345102_, DamageSource p_21385_, boolean p_21387_) {
        super.dropCustomDeathLoot(p_345102_, p_21385_, p_21387_);
        // OLD RANDOM DROP LOOT //
        /*List<Item> Lootitems = Arrays.asList(
                Items.DIAMOND,
                Items.GOLD_INGOT,
                Items.IRON_INGOT,
                Items.EMERALD,
                Items.LAPIS_LAZULI,
                Items.REDSTONE,
                Items.COAL,
                Items.QUARTZ,
                Items.NETHERITE_SCRAP,
                Items.NETHERITE_INGOT,
                Items.NETHERITE_SWORD,
                Items.NETHERITE_PICKAXE,
                Items.NETHERITE_AXE,
                Items.NETHERITE_SHOVEL,
                Items.NETHERITE_HOE,
                Items.NETHERITE_HELMET,
                Items.NETHERITE_CHESTPLATE,
                Items.NETHERITE_LEGGINGS,
                Items.NETHERITE_BOOTS,
                Items.DIAMOND_SWORD,
                Items.DIAMOND_PICKAXE,
                Items.DIAMOND_AXE,
                Items.DIAMOND_SHOVEL,
                Items.DIAMOND_HOE,
                Items.DIAMOND_HELMET,
                Items.DIAMOND_CHESTPLATE,
                Items.DIAMOND_LEGGINGS,
                Items.DIAMOND_BOOTS,
                Items.GOLDEN_SWORD,
                Items.GOLDEN_PICKAXE,
                Items.GOLDEN_AXE,
                Items.GOLDEN_SHOVEL,
                Items.GOLDEN_HOE,
                Items.GOLDEN_HELMET,
                Items.GOLDEN_CHESTPLATE,
                Items.GOLDEN_LEGGINGS,
                Items.GOLDEN_BOOTS,
                Items.IRON_SWORD,
                Items.IRON_PICKAXE,
                Items.IRON_AXE,
                Items.IRON_SHOVEL,
                Items.IRON_HOE,
                Items.IRON_HELMET,
                Items.IRON_CHESTPLATE,
                Items.IRON_LEGGINGS,
                Items.IRON_BOOTS,
                Items.STONE_SWORD,
                Items.STONE_PICKAXE,
                Items.STONE_AXE,
                Items.STONE_SHOVEL,
                Items.STONE_HOE,
                Items.CHAINMAIL_HELMET,
                Items.CHAINMAIL_CHESTPLATE,
                Items.CHAINMAIL_LEGGINGS,
                Items.CHAINMAIL_BOOTS,
                Items.LEATHER_HELMET,
                Items.LEATHER_CHESTPLATE,
                Items.LEATHER_LEGGINGS,
                Items.LEATHER_BOOTS,
                Items.WOODEN_SWORD,
                Items.WOODEN_PICKAXE,
                Items.WOODEN_AXE,
                Items.WOODEN_SHOVEL,
                Items.WOODEN_HOE,
                Items.BOW,
                Items.ARROW,
                Items.CROSSBOW,
                Items.TIPPED_ARROW,
                Items.SPECTRAL_ARROW,
                Items.SHIELD,
                Items.TRIDENT,
                Items.FISHING_ROD,
                Items.CARROT_ON_A_STICK,
                Items.WARPED_FUNGUS_ON_A_STICK,
                Items.SADDLE,
                Items.LEAD,
                Items.NAME_TAG,
                Items.BOOK,
                Items.ENCHANTED_BOOK,
                Items.WRITTEN_BOOK,
                Items.WRITABLE_BOOK,
                Items.INK_SAC,
                Items.FEATHER,
                Items.LEATHER,
                Items.RABBIT_HIDE,
                Items.STRING,
                Items.BONE,
                Items.ENDER_PEARL,
                Items.ENDER_EYE,
                Items.GHAST_TEAR,
                Items.GUNPOWDER,
                Items.BLAZE_POWDER,
                Items.MAGMA_CREAM,
                Items.SLIME_BALL,
                Items.SPIDER_EYE,
                Items.FERMENTED_SPIDER_EYE,
                Items.PHANTOM_MEMBRANE,
                Items.NETHER_STAR,
                Items.NETHER_WART,
                Items.GLOWSTONE_DUST,
                Items.REDSTONE,
                Items.GLOWSTONE,
                Items.QUARTZ,
                Items.PRISMARINE_SHARD,
                Items.PRISMARINE_CRYSTALS,
                Items.SHULKER_SHELL,
                Items.SHULKER_BOX,
                Items.ELYTRA,
                Items.TOTEM_OF_UNDYING,
                Items.DRAGON_BREATH,
                Items.EXPERIENCE_BOTTLE,
                Items.ENCHANTING_TABLE,
                Items.ANVIL,
                Items.CHIPPED_ANVIL,
                Items.DAMAGED_ANVIL,
                Items.GRINDSTONE,
                Items.SMITHING_TABLE,
                Items.SMOKER,
                Items.BLAST_FURNACE,
                Items.CARTOGRAPHY_TABLE,
                Items.FLETCHING_TABLE,
                Items.SMITHING_TABLE,
                Items.STONECUTTER,
                Items.BREWING_STAND,
                Items.CAULDRON,
                Items.BELL,
                Items.LOOM,
                Items.COMPOSTER,
                Items.BARREL,
                Items.BEEHIVE,
                Items.BEE_NEST,
                Items.CAMPFIRE,
                Items.SOUL_CAMPFIRE,
                Items.LANTERN,
                Items.SOUL_LANTERN,
                Items.TORCH,
                Items.SOUL_TORCH,
                Items.REDSTONE_TORCH,
                Items.REDSTONE_LAMP,
                Items.REDSTONE_BLOCK,
                Items.BEEF,
                Items.PORKCHOP,
                Items.CHICKEN,
                Items.MUTTON,
                Items.RABBIT,
                Items.COD,
                Items.SALMON,
                Items.TROPICAL_FISH,
                Items.PUFFERFISH,
                Items.COOKED_BEEF,
                Items.COOKED_PORKCHOP,
                Items.COOKED_CHICKEN,
                Items.COOKED_MUTTON,
                Items.COOKED_RABBIT,
                Items.COOKED_COD,
                Items.COOKED_SALMON,
                Items.COBBLESTONE,
                Items.STONE,
                Items.GRANITE,
                Items.ACACIA_LOG,
                Items.BIRCH_LOG,
                Items.DARK_OAK_LOG,
                Items.JUNGLE_LOG,
                Items.OAK_LOG,
                Items.SPRUCE_LOG,
                Items.ACACIA_PLANKS,
                Items.BIRCH_PLANKS,
                Items.DARK_OAK_PLANKS,
                Items.JUNGLE_PLANKS,
                Items.OAK_PLANKS,
                Items.SPRUCE_PLANKS,
                Items.ACACIA_SLAB,
                Items.BIRCH_SLAB,
                Items.DARK_OAK_SLAB,
                Items.JUNGLE_SLAB,
                Items.OAK_SLAB,
                Items.SPRUCE_SLAB,
                Items.ACACIA_STAIRS,
                Items.BIRCH_STAIRS,
                Items.DARK_OAK_STAIRS,
                Items.JUNGLE_STAIRS,
                Items.OAK_STAIRS,
                Items.SPRUCE_STAIRS,
                Items.ACACIA_FENCE,
                Items.BIRCH_FENCE,
                Items.DARK_OAK_FENCE,
                Items.JUNGLE_FENCE,
                Items.OAK_FENCE,
                Items.SPRUCE_FENCE,
                Items.ACACIA_FENCE_GATE,
                Items.BIRCH_FENCE_GATE,
                Items.DARK_OAK_FENCE_GATE,
                Items.JUNGLE_FENCE_GATE,
                Items.OAK_FENCE_GATE,
                Items.SPRUCE_FENCE_GATE,
                Items.ACACIA_DOOR,
                Items.BIRCH_DOOR,
                Items.DARK_OAK_DOOR,
                Items.JUNGLE_DOOR,
                Items.OAK_DOOR,
                Items.SPRUCE_DOOR,
                Items.ACACIA_TRAPDOOR,
                Items.BIRCH_TRAPDOOR,
                Items.DARK_OAK_TRAPDOOR,
                Items.JUNGLE_TRAPDOOR,
                Items.OAK_TRAPDOOR,
                Items.SPRUCE_TRAPDOOR
        );

        // Choose number of items to drop between 1 and 32
        int numberOfItems = this.random.nextInt(32) + 1;

        // Now drop the items
        for (int i = 0; i < numberOfItems; i++) {
            boolean isBlock = this.random.nextInt(2) == 0;
            if (isBlock) {
                List<Item> blockItems = Arrays.asList(
                        Items.STONE,
                        Items.OAK_LOG,
                        Items.OAK_PLANKS,
                        Items.COBBLESTONE
                );
                Item blockItem = blockItems.get(this.random.nextInt(blockItems.size()));
                this.spawnAtLocation(new ItemStack(blockItem), p_345102_);
            }
            else
            {
                Item item = Lootitems.get(this.random.nextInt(Lootitems.size()));
                this.spawnAtLocation(new ItemStack(item), p_345102_);
            }
        }*/

        // Drop all items from the inventory
        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack itemStack = this.inventory.getItem(i);
            if (!itemStack.isEmpty()) {
                this.spawnAtLocation(itemStack, p_345102_);
            }
        }
    }

    private void spawnAtLocation(ItemStack itemStack, ServerLevel level) {
        ItemEntity itemEntity = new ItemEntity(level, this.getX(), this.getY(), this.getZ(), itemStack);
        level.addFreshEntity(itemEntity);
    }

    @Override
    public boolean canHoldItem(ItemStack p_21545_) {
        return super.canHoldItem(p_21545_);
    }

    // Inventory Logic
    @Override
    public SimpleContainer getInventory() {
        return this.inventory;
    }

    @Override
    public SlotAccess getSlot(int p_149995_) {
        int i = p_149995_ - 300;
        return i >= 0 && i < this.inventory.getContainerSize() ? SlotAccess.forContainer(this.inventory, i) : super.getSlot(p_149995_);
    }

    @Override
    protected void pickUpItem(ServerLevel p_363972_, ItemEntity p_21471_) {
        super.pickUpItem(p_363972_, p_21471_);
        inventory.addItem(p_21471_.getItem());
        p_21471_.discard();
        //InventoryCarrier.pickUpItem(p_363972_, this, this, p_21471_);
    }

    @Override
    protected Vec3i getPickupReach() {
        return super.getPickupReach();
    }

    @Override
    public void setCanPickUpLoot(boolean p_21554_) {
        super.setCanPickUpLoot(p_21554_);
    }

    @Override
    public boolean wantsToPickUp(ServerLevel p_367521_, ItemStack p_21546_) {
        return super.wantsToPickUp(p_367521_, p_21546_);
    }

    // Add to save data
    @Override
    public void addAdditionalSaveData(CompoundTag p_34458_) {
        super.addAdditionalSaveData(p_34458_);
        this.addPersistentAngerSaveData(p_34458_);
        p_34458_.putString("EntityName", ENTITY_NAME);
        p_34458_.putString("EntityUUID", entityUUID);
        p_34458_.putString("CustomSkin", this.getCustomSkin().toString());
        p_34458_.putInt("RemainingPersistentAngerTime", this.remainingPersistentAngerTime);
        p_34458_.putInt("InventorySize", this.FAKEPLAYER_INVENTORY_SIZE);
        // Save the inventory
        ListTag listTag = new ListTag();
        this.save(listTag);
        p_34458_.put("Inventory", listTag);
    }

    // Read from save data
    @Override
    public void readAdditionalSaveData(CompoundTag p_34446_) {
        super.readAdditionalSaveData(p_34446_);
        this.readPersistentAngerSaveData(this.level(), p_34446_);
        ENTITY_NAME = p_34446_.getString("EntityName");
        entityUUID = p_34446_.getString("EntityUUID");
        this.setCustomSkin(ResourceLocation.tryParse(p_34446_.getString("CustomSkin")));
        this.remainingPersistentAngerTime = p_34446_.getInt("RemainingPersistentAngerTime");
        this.FAKEPLAYER_INVENTORY_SIZE = p_34446_.getInt("InventorySize");
        // Load the inventory
        ListTag listTag = p_34446_.getList("Inventory", 10);
        this.load(listTag);
    }

    @Override
    public void die(DamageSource p_21014_) {
        super.die(p_21014_);
        if (!this.level().isClientSide) {
            Entity entity = p_21014_.getEntity();
            if (entity instanceof LivingEntity) {
                Component deathMessage = Component.translatable("death.attack." + p_21014_.getMsgId(), this.getDisplayName(), entity.getDisplayName());
                this.level().getServer().getPlayerList().broadcastSystemMessage(deathMessage, false);
            }
            else {
                Component deathMessage = Component.translatable("death.attack." + p_21014_.getMsgId(), this.getDisplayName());
                this.level().getServer().getPlayerList().broadcastSystemMessage(deathMessage, false);
            }

            Component deathMessage = Component.translatable("§e"+ this.getDisplayName().getString() +" left the game");
            this.level().getServer().getPlayerList().broadcastSystemMessage(deathMessage, false);
        }
    }

    public FakePlayerRenderer getRenderer() {
        return (FakePlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(this);
    }

    // Inventory Logic Methods - Save
    public ListTag save(ListTag p_36027_) {
        for(int i = 0; i < this.inventory.getItems().size(); ++i) {
            if (!((ItemStack)this.inventory.getItems().get(i)).isEmpty()) {
                CompoundTag compoundtag = new CompoundTag();
                compoundtag.putByte("Slot", (byte)i);
                p_36027_.add(((ItemStack)this.inventory.getItems().get(i)).save(this.registryAccess(), compoundtag));
            }
        }
        return p_36027_;
    }

    // Inventory Logic Methods - Load
    public void load(ListTag p_36036_) {
        this.inventory.clearContent();

        for(int i = 0; i < p_36036_.size(); ++i) {
            CompoundTag compoundtag = p_36036_.getCompound(i);
            int j = compoundtag.getByte("Slot") & 255;
            ItemStack itemstack = (ItemStack)ItemStack.parse(this.registryAccess(), compoundtag).orElse(ItemStack.EMPTY);
            if (j >= 0 && j < this.inventory.getItems().size()) {
                this.inventory.getItems().set(j, itemstack);
            }
        }
    }
}
