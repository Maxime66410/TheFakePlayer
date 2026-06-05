// Original source
package org.furranystudio.thefakeplayer.Entity;

// org imports
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.GameType;
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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import org.furranystudio.thefakeplayer.Entity.Goals.FakePlayerChestGoal;
import org.furranystudio.thefakeplayer.Entity.Goals.FakePlayerCraftGoal;
import org.furranystudio.thefakeplayer.Entity.Goals.FakePlayerEatGoal;
import org.furranystudio.thefakeplayer.Entity.Goals.FakePlayerHarvestGoal;
import org.furranystudio.thefakeplayer.Entity.Goals.FakePlayerMineGoal;
import org.furranystudio.thefakeplayer.Entity.Goals.FakePlayerCreeperFleeGoal;
import org.furranystudio.thefakeplayer.Entity.Goals.FakePlayerFleeGoal;
import org.furranystudio.thefakeplayer.Entity.Goals.FakePlayerPotionGoal;
import org.furranystudio.thefakeplayer.Entity.Goals.FakePlayerLongDistanceTravelGoal;
import org.furranystudio.thefakeplayer.Entity.Goals.FakePlayerWanderGoal;
import org.furranystudio.thefakeplayer.Entity.Goals.FakePlayerWeaponSelectGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.core.component.DataComponents;
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

public class FakePlayerEntity extends PathfinderMob implements NeutralMob, InventoryCarrier {

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

    // Animation variables
    public final AnimationState idleAnimationState = new AnimationState();
    public float maxCrossbowChargeDuration = 2.0F;
    public int ticksUsingItem;
    public boolean isCrouching;
    public double speedValue;
    public int shieldCooldown = 0;
    public boolean godMode = false;
    public int suppressTargetingTicks = 0;

    private static final ResourceLocation CRIT_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("thefakeplayer", "crit_hit");

    // Tab list / chat
    private boolean hasTabListEntry = false;
    private boolean initialized = false;
    private boolean profileReady = false; // true when name+skin are loaded (thread done)
    private int chatTimer = 20 * 60 * 3; // first message after 3 minutes

    // Raw Mojang skin data (base64 value + signature for GameProfile)
    private String skinTextureValue = null;
    private String skinTextureSignature = null;
    private String skinUrl = "";

    // EntityData to sync the skin URL from server → clients automatically
    private static final EntityDataAccessor<String> SKIN_URL =
        SynchedEntityData.defineId(FakePlayerEntity.class, EntityDataSerializers.STRING);
    private String lastSkinUrl = "";

    // EntityData to sync animations from server → clients
    private static final EntityDataAccessor<Integer> EAT_ANIM_TICK =
        SynchedEntityData.defineId(FakePlayerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SWING_ANIM_TICK =
        SynchedEntityData.defineId(FakePlayerEntity.class, EntityDataSerializers.INT);

    // Client-side interpolation to smooth swing animation (60fps)
    public float oSwingAnimFrac = 0.0F; // previous tick value
    public float swingAnimFrac = 0.0F;  // current tick value

    private static final String[] CHAT_MESSAGES = {
        "lol",
        "gg",
        "anyone there?",
        "nice base",
        "what are you building?",
        "found diamonds",
        "brb",
        "back",
        "this seed is insane",
        "...",
        "mining for a bit",
        "wanna go mining?",
        "hungry lol",
        "watch out creeper behind you",
        "going exploring for a bit",
        "ok crafting",
        "got any wood?",
        "almost died",
        "nice one",
        "how far are we from spawn?"
    };


    // Constructeurs
    public FakePlayerEntity(EntityType<? extends PathfinderMob> entityType, Level world) {
        super(entityType, world);
    }

    public FakePlayerEntity(EntityType<? extends PathfinderMob> entityType, Level world, String playerName)
    {
        super(entityType, world);
        UpdateEntityProfile(playerName);
        this.setCanPickUpLoot(true);
    }

    public FakePlayerEntity(EntityType<? extends PathfinderMob> entityType, Level world, double x, double y, double z) {
        super(entityType, world);
        this.setPos(x, y, z);
    }

    public FakePlayerEntity(EntityType<? extends PathfinderMob> entityType, Level world, double x, double y, double z, String playerName) {
        super(entityType, world);
        this.setPos(x, y, z);
        UpdateEntityProfile(playerName);
    }

    public FakePlayerEntity(EntityType<? extends PathfinderMob> entityType, Level world, BlockPos pos) {
        super(entityType, world);
        this.setPos(pos.getX(), pos.getY(), pos.getZ());
    }

    public FakePlayerEntity(EntityType<? extends PathfinderMob> entityType, Level world, BlockPos pos, String playerName) {
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
        UpdateEntityProfile(); // Must be after super() — field initializers run after super() and would overwrite skinTextureValue
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

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN_URL, "");
        builder.define(EAT_ANIM_TICK, 0);
        builder.define(SWING_ANIM_TICK, 0);
    }

    // Animation sync accessors
    public int getEatAnimTick() { return this.entityData.get(EAT_ANIM_TICK); }
    public void setEatAnimTick(int tick) { this.entityData.set(EAT_ANIM_TICK, tick); }
    public int getSwingAnimTick() { return this.entityData.get(SWING_ANIM_TICK); }

    /** Triggers the swing animation (harvest, attack…). Fixed duration of 10 ticks. */
    public void triggerSwingAnim() {
        if (!this.level().isClientSide()) {
            this.entityData.set(SWING_ANIM_TICK, 10);
        }
    }

    // Removal: removes from tab list + disconnection message
    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide() && hasTabListEntry) {
            removeFromTabList();
            hasTabListEntry = false;
            broadcastLeaveMessage();
        }
        super.remove(reason);
    }

    // Builds the tab list packet for this fake player via reflection
    // The public constructor only takes Collection<ServerPlayer>, not List<Entry>
    @SuppressWarnings("unchecked")
    private ClientboundPlayerInfoUpdatePacket buildTabListPacket() {
        try {
            GameProfile profile = new GameProfile(this.getUUID(), this.getName().getString());

            // Add Mojang texture to profile — signature required for the client to accept it
            if (skinTextureValue != null && skinTextureSignature != null && !skinTextureSignature.isEmpty()) {
                profile.getProperties().put("textures", new Property(
                    "textures", skinTextureValue, skinTextureSignature
                ));
            }

            ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
                this.getUUID(), profile, true, 0, GameType.SURVIVAL, null, true, 0, null
            );

            EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = EnumSet.of(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE
            );

            // The public constructor requires Collection<ServerPlayer> — we pass an empty list
            // via unchecked cast (safe at runtime due to Java erasure)
            Collection<ServerPlayer> emptyPlayers = (Collection<ServerPlayer>)(Collection<?>) Collections.emptyList();
            ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(actions, emptyPlayers);

            // Replace the entries field with our custom entry (official Mojang field name)
            java.lang.reflect.Field entriesField = ClientboundPlayerInfoUpdatePacket.class.getDeclaredField("entries");
            entriesField.setAccessible(true);
            entriesField.set(packet, List.of(entry));

            return packet;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to build FakePlayer tab list packet", e);
        }
    }

    // Sends the add-to-tab-list packet to all connected players
    public void addToTabList() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        ClientboundPlayerInfoUpdatePacket packet = buildTabListPacket();
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }

    // Sends the tab list entry to a specific player (for players who join later)
    public void sendTabListEntryTo(ServerPlayer player) {
        player.connection.send(buildTabListPacket());
    }

    // Sends the remove-from-tab-list packet to all connected players
    private void removeFromTabList() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        MinecraftServer server = serverLevel.getServer();

        ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(
            List.of(this.getUUID())
        );

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }

    // Broadcast "joined the game" to all players
    private void broadcastJoinMessage() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        serverLevel.getServer().getPlayerList().broadcastSystemMessage(
            Component.literal("§e" + this.getName().getString() + " joined the game"), false
        );
    }

    // Broadcast "left the game" to all players
    private void broadcastLeaveMessage() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        serverLevel.getServer().getPlayerList().broadcastSystemMessage(
            Component.literal("§e" + this.getName().getString() + " left the game"), false
        );
    }

    // Sends a random chat message on behalf of the fake player
    private void sendRandomChatMessage() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        String msg = CHAT_MESSAGES[this.random.nextInt(CHAT_MESSAGES.length)];
        serverLevel.getServer().getPlayerList().broadcastSystemMessage(
            Component.literal("<" + this.getName().getString() + "> " + msg), false
        );
        // Reset timer between 3 and 10 minutes
        chatTimer = 20 * 60 * (3 + this.random.nextInt(8));
    }

    public net.minecraft.world.entity.ai.goal.GoalSelector getGoalSelector() { return this.goalSelector; }
    public net.minecraft.world.entity.ai.goal.GoalSelector getTargetSelector() { return this.targetSelector; }

    // Methods - Entity for FakePlayerEntity
    @Override
    protected void registerGoals() {

        // Add goals to the entity
        this.goalSelector.addGoal(0, new FloatGoal(this)); // Float in water
        this.goalSelector.addGoal(1, new FakePlayerEatGoal(this));
        this.goalSelector.addGoal(1, new FakePlayerCreeperFleeGoal(this));
        this.goalSelector.addGoal(1, new FakePlayerFleeGoal(this));
        this.goalSelector.addGoal(1, new FakePlayerPotionGoal(this));
        this.goalSelector.addGoal(2, new FakePlayerWeaponSelectGoal(this));
        this.goalSelector.addGoal(4, new FakePlayerHarvestGoal(this));
        this.goalSelector.addGoal(5, new FakePlayerChestGoal(this));
        this.goalSelector.addGoal(6, new FakePlayerMineGoal(this));
        this.goalSelector.addGoal(7, new FakePlayerCraftGoal(this));
        this.goalSelector.addGoal(7, new FakePlayerLongDistanceTravelGoal(this));
        this.goalSelector.addGoal(7, new FakePlayerWanderGoal(this)); // Random roaming — 50 block radius
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F)); // Look at player
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true)); // Melee attack
        this.goalSelector.addGoal(3, new BreakDoorGoal(this, (HARD) -> {
            return true;
        })); // Break doors
        this.goalSelector.addGoal(2, new OpenDoorGoal(this, true)); // Open doors
        this.goalSelector.addGoal(5, new PanicGoal(this, 1.25D)); // Panic
        this.goalSelector.addGoal(2, new EatBlockGoal(this)); // Eat grass blocks
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<Mob>(this, Mob.class, 10, false, false, this::isHostileMob));
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<Animal>(this, Animal.class, 10, false, false, this::isHuntableAnimal));
        this.targetSelector.addGoal(6, new ResetUniversalAngerTargetGoal<>(this, false));
        this.goalSelector.addGoal(3, new MoveThroughVillageGoal(this, 1.0, true, 4, this::canBreakDoors)); // Move through village

        this.setCanPickUpLoot(true);
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.LAVA, -1.0f);
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.DAMAGE_FIRE, -1.0f);
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.DANGER_FIRE, -1.0f);
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.DAMAGE_OTHER, -1.0f);
    }

    // Update the entity's profile — HTTP on a separate thread to avoid blocking the server
    private void UpdateEntityProfile()
    {
        // No name displayed until the profile is loaded
        new Thread(() -> {
            try {
                if (!hasInternetConnection()) {
                    this.level().getServer().execute(() -> {
                        this.setCustomName(Component.literal("Steve"));
                        profileReady = true;
                        if (!hasTabListEntry) { addToTabList(); hasTabListEntry = true; broadcastJoinMessage(); }
                    });
                    return;
                }
                for (int attempt = 0; attempt < 5; attempt++) {
                    String playerName = getRandomName();
                    String playerUUID = getUUIDFromName(playerName);
                    if (playerUUID == null) continue;
                    String[] skinData = getSkinProperty(playerUUID);
                    if (skinData == null) continue;

                    final String finalName = playerName;
                    final String finalUUID = playerUUID;
                    final String[] finalSkinData = skinData;
                    this.level().getServer().execute(() -> {
                        this.setCustomName(Component.literal(finalName));
                        setEntityName(finalName);
                        setEntityUUID(finalUUID);
                        profileReady = true;
                        applySkin(finalName, finalSkinData);
                        // If tick() hasn't added to the tab yet, do it now
                        if (!hasTabListEntry) { addToTabList(); hasTabListEntry = true; broadcastJoinMessage(); }
                    });
                    return;
                }
                // Fallback: no valid name found in 5 attempts
                this.level().getServer().execute(() -> {
                    this.setCustomName(Component.literal("Steve"));
                    profileReady = true;
                    if (!hasTabListEntry) { addToTabList(); hasTabListEntry = true; broadcastJoinMessage(); }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "FakePlayer-SkinLoader").start();
    }

    private void UpdateEntityProfile(String playerName)
    {
        // Name known immediately → profileReady = true so tick() can broadcast the join right away
        this.setCustomName(Component.literal(playerName));
        profileReady = true;
        new Thread(() -> {
            try {
                if (!hasInternetConnection()) return;
                String playerUUID = getUUIDFromName(playerName);
                if (playerUUID == null) return;
                String[] skinData = getSkinProperty(playerUUID);
                if (skinData == null) return;

                final String finalUUID = playerUUID;
                final String[] finalSkinData = skinData;
                this.level().getServer().execute(() -> {
                    setEntityName(playerName);
                    setEntityUUID(finalUUID);
                    applySkin(playerName, finalSkinData);
                    // If tick() hasn't added to the tab yet (very rare), do it now
                    if (!hasTabListEntry) { addToTabList(); hasTabListEntry = true; broadcastJoinMessage(); }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "FakePlayer-SkinLoader").start();
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
                .add(Attributes.ATTACK_SPEED, 2.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D)
                .add(Attributes.BLOCK_BREAK_SPEED, 5.0D)
                .add(Attributes.MINING_EFFICIENCY, 5.0D)
                .add(Attributes.BLOCK_INTERACTION_RANGE, 6.0D);

    }

    // Get Custom Skin Location
    public ResourceLocation getCustomSkin() {
        return customSkin != null ? customSkin : ResourceLocation.fromNamespaceAndPath(Thefakeplayer.MODID,"textures/entities/basefakeplayer.png");  // Default skin if none is set
    }

    // Set Custom Skin Location
    public void setCustomSkin(ResourceLocation skin) {
        this.customSkin = skin;

        // Texture update only on client side (Minecraft.getInstance() doesn't exist server-side)
        if (this.level().isClientSide()) {
            ResourceLocation skinLocation = ResourceLocation.fromNamespaceAndPath(Thefakeplayer.MODID, "config/thefakeplayer/" + skin.getPath());
            File skinFile = new File(skinLocation.getPath());
            FakePlayerRenderer.updateTextureFromFile(skinFile);
        }

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

    // Returns [skinUrl, base64Value, signature] from the Mojang UUID, or null if not found
    public static String[] getSkinProperty(String uuid) throws IOException {
        URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
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

        JsonObject jsonObject = JsonParser.parseString(responseBuilder.toString()).getAsJsonObject();

        if (jsonObject.has("properties")) {
            JsonArray properties = jsonObject.getAsJsonArray("properties");
            for (JsonElement element : properties) {
                JsonObject property = element.getAsJsonObject();
                if (property.get("name").getAsString().equals("textures")) {
                    String base64Value = property.get("value").getAsString();
                    String signature = property.has("signature") ? property.get("signature").getAsString() : null;
                    String decoded = new String(Base64.getDecoder().decode(base64Value));
                    JsonObject textureData = JsonParser.parseString(decoded).getAsJsonObject();
                    String skinUrl = textureData.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
                    return new String[]{ skinUrl, base64Value, signature };
                }
            }
        }

        return null;
    }

    // Compat: returns only the skin URL (used internally)
    public static String getSkinURL(String uuid) throws IOException {
        String[] data = getSkinProperty(uuid);
        return data != null ? data[0] : null;
    }

    // Download and save skin
    public static ResourceLocation downloadAndSaveSkin(String skinURL, String playerName) throws IOException {
        // Before downloading the skin, check if the skins folder exists
        File skinsDir = new File("config/"+Thefakeplayer.MODID+"/skins");
        if (!skinsDir.exists()) {
            skinsDir.mkdirs();
        }

        String safePlayerName = playerName.toLowerCase().replaceAll("[^a-z0-9_-]", "_");

        // Check if the skin already exists
        File skinFile = new File(skinsDir, safePlayerName + ".png");
        if (skinFile.exists()) {
            return ResourceLocation.fromNamespaceAndPath(Thefakeplayer.MODID, "skins/" + safePlayerName + ".png");
        }

        BufferedImage skinImage = ImageIO.read(new URL(skinURL));
        File newSkinFile = new File("config/" +Thefakeplayer.MODID + "/skins/" + safePlayerName + ".png");
        newSkinFile.getParentFile().mkdirs();
        ImageIO.write(skinImage, "png", newSkinFile);

        // Return the skin path in a valid ResourceLocation format
        return ResourceLocation.fromNamespaceAndPath(Thefakeplayer.MODID, "skins/" + safePlayerName + ".png");
    }

    // Applies pre-fetched skin data (avoids a double call to the session server)
    public void applySkin(String playerName, String[] skinData) {
        try {
            skinUrl              = skinData[0];
            skinTextureValue     = skinData[1];
            skinTextureSignature = skinData[2];

            setCustomSkin(downloadAndSaveSkin(skinUrl, playerName));

            // Synchroniser l'URL du skin vers tous les clients via EntityData (MC vanilla)
            this.entityData.set(SKIN_URL, skinUrl);

            // If already in the tab list, remove + readd to force the client to accept the new profile
            // (ADD_PLAYER for an existing UUID is ignored by the client)
            if (hasTabListEntry) {
                removeFromTabList();
                addToTabList();
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
            connection.setConnectTimeout(3000); // 3-second timeout
            connection.connect();
            return (connection.getResponseCode() == 200);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    protected void actuallyHurt(ServerLevel level, DamageSource source, float amount) {
        // Vanilla already set amount=0 if isDamageSourceBlocked()=true — we check isBlocking() directly
        if (this.isBlocking()) {
            Entity attacker = source.getDirectEntity();
            boolean piercing = attacker instanceof net.minecraft.world.entity.projectile.AbstractArrow arr
                    && arr.getPierceLevel() > 0;
            if (!piercing) {
                this.playSound(net.minecraft.sounds.SoundEvents.SHIELD_BLOCK, 1.0F,
                        0.8F + this.random.nextFloat() * 0.4F);
                if (attacker instanceof LivingEntity le) {
                    this.setTarget(le);
                }
                return;
            }
        }

        super.actuallyHurt(level, source, amount);

        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity le) {
            this.setTarget(le);
        }

        if (this.isDeadOrDying()) {
            if (attacker instanceof LivingEntity le) {
                le.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 10));
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 3));
            }
            dropCustomDeathLoot(level, source, true);
            this.remove(RemovalReason.DISCARDED);
        }
    }

    public static boolean canSpawn(EntityType<? extends FakePlayerEntity> p_223365_, LevelAccessor p_223366_, EntitySpawnReason p_223367_, BlockPos p_223368_, RandomSource p_223369_) {
        return Mob.checkMobSpawnRules(p_223365_, p_223366_, p_223367_, p_223368_, p_223369_);
    }

    public boolean canBreakDoors() {
        return true;
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

        // Drop all items from the inventory
        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack itemStack = this.inventory.getItem(i);
            if (!itemStack.isEmpty()) {
                this.spawnAtLocation(itemStack, p_345102_);
            }
        }

        // Drop item from hand
        if(!this.getMainHandItem().isEmpty()) {
            this.spawnAtLocation(this.getMainHandItem(), p_345102_);
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

    public boolean hasFood() {
        if (this.getMainHandItem().has(DataComponents.FOOD)) return true;
        if (this.getOffhandItem().has(DataComponents.FOOD)) return true;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).has(DataComponents.FOOD)) return true;
        }
        return false;
    }

    public boolean hasHealingPotion() {
        if (isHealingPotion(this.getMainHandItem())) return true;
        if (isHealingPotion(this.getOffhandItem())) return true;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isHealingPotion(inventory.getItem(i))) return true;
        }
        return false;
    }

    private boolean isHealingPotion(ItemStack stack) {
        net.minecraft.world.item.alchemy.PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return false;
        for (net.minecraft.world.effect.MobEffectInstance eff : contents.getAllEffects()) {
            if (eff.getEffect() == net.minecraft.world.effect.MobEffects.HEAL
             || eff.getEffect() == net.minecraft.world.effect.MobEffects.REGENERATION) {
                return true;
            }
        }
        return false;
    }

    private boolean isHostileMob(LivingEntity e, ServerLevel level) {
        return suppressTargetingTicks <= 0 && e instanceof Enemy && e != this && this.getHealth() > this.getMaxHealth() * 0.2f;
    }

    private boolean isHuntableAnimal(LivingEntity e, ServerLevel level) {
        return suppressTargetingTicks <= 0 && !hasFood() && this.getHealth() > this.getMaxHealth() * 0.2f;
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
    protected void pickUpItem(ServerLevel level, ItemEntity itemEntity) {
        super.pickUpItem(level, itemEntity);
        if (!itemEntity.isRemoved() && !itemEntity.getItem().isEmpty()) {
            ItemStack leftover = inventory.addItem(itemEntity.getItem().copy());
            if (leftover.getCount() < itemEntity.getItem().getCount()) {
                this.onItemPickup(itemEntity);
                if (leftover.isEmpty()) itemEntity.discard();
                else itemEntity.setItem(leftover);
            }
        }
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
    public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
        return !stack.isEmpty();
    }

    // Add to save data
    @Override
    public void addAdditionalSaveData(CompoundTag p_34458_) {
        super.addAdditionalSaveData(p_34458_);
        this.addPersistentAngerSaveData(p_34458_);
        this.setNoAi(false);
        p_34458_.putString("EntityName", ENTITY_NAME);
        p_34458_.putString("EntityUUID", entityUUID);
        p_34458_.putString("CustomSkin", this.getCustomSkin().toString());
        p_34458_.putInt("RemainingPersistentAngerTime", this.remainingPersistentAngerTime);
        p_34458_.putInt("InventorySize", this.FAKEPLAYER_INVENTORY_SIZE);
        if (skinTextureValue != null) {
            p_34458_.putString("SkinTextureValue", skinTextureValue);
            p_34458_.putString("SkinTextureSignature", skinTextureSignature != null ? skinTextureSignature : "");
            p_34458_.putString("SkinUrl", skinUrl);
        }
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
        if (p_34446_.contains("SkinTextureValue")) {
            skinTextureValue = p_34446_.getString("SkinTextureValue");
            String sig = p_34446_.getString("SkinTextureSignature");
            skinTextureSignature = sig.isEmpty() ? null : sig;
            skinUrl = p_34446_.getString("SkinUrl");
            profileReady = true; // Profile restored from NBT, no need to wait for the thread
            if (!skinUrl.isEmpty()) {
                this.entityData.set(SKIN_URL, skinUrl);
            }
        }
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
            } else {
                Component deathMessage = Component.translatable("death.attack." + p_21014_.getMsgId(), this.getDisplayName());
                this.level().getServer().getPlayerList().broadcastSystemMessage(deathMessage, false);
            }
            // "left the game" is handled in remove() to cover all cases
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

    @Override
    public void tick() {
        speedValue = this.getDeltaMovement().length();

        if (level().isClientSide()) {
            this.idleAnimationState.animateWhen(!isInWaterOrBubble() && !this.walkAnimation.isMoving(), this.tickCount);

            // Swing interpolation: save the old value before updating
            oSwingAnimFrac = swingAnimFrac;
            swingAnimFrac = (10 - this.entityData.get(SWING_ANIM_TICK)) / 10.0F;

            // Apply skin as soon as the URL is synced from the server
            String skinUrl = this.entityData.get(SKIN_URL);
            if (!skinUrl.isEmpty() && !skinUrl.equals(lastSkinUrl)) {
                lastSkinUrl = skinUrl;
                org.furranystudio.thefakeplayer.Entity.Renderer.FakePlayerRenderer.updateTextureFromURL(skinUrl);
            }
        } else {
            if (!initialized && profileReady) {
                initialized = true;
                if (!hasTabListEntry) {
                    addToTabList();
                    hasTabListEntry = true;
                    broadcastJoinMessage();
                }
            }
            if (chatTimer > 0) {
                chatTimer--;
            } else {
                sendRandomChatMessage();
            }
        }

        super.tick();

        // Decrement swing counter (2 per tick → 5 ticks total = 0.25s)
        if (!level().isClientSide()) {
            int swing = this.entityData.get(SWING_ANIM_TICK);
            if (swing > 0) {
                this.entityData.set(SWING_ANIM_TICK, Math.max(0, swing - 2));
            }
            if (shieldCooldown > 0) shieldCooldown--;
            if (suppressTargetingTicks > 0) suppressTargetingTicks--;
            if (godMode) this.setHealth(this.getMaxHealth() * 10);
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
        if (this.isUsingItem() && this.getOffhandItem().getItem() instanceof net.minecraft.world.item.ShieldItem) {
            this.stopUsingItem();
        }
        this.shieldCooldown = 25;

        boolean isCrit = !this.onGround() && this.fallDistance > 0.0F
                && !this.isInWater() && !this.onClimbable();

        AttributeInstance atk = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (isCrit && atk != null) {
            atk.addTransientModifier(new AttributeModifier(CRIT_MODIFIER_ID, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }

        boolean result = super.doHurtTarget(level, target);

        if (isCrit && atk != null) {
            atk.removeModifier(CRIT_MODIFIER_ID);
            if (result) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                        15, 0.3, 0.3, 0.3, 0.1);
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }

        if (result) triggerSwingAnim();
        return result;
    }

    @Override
    public boolean isBlocking() {
        return this.isUsingItem() && this.getOffhandItem().getItem() instanceof net.minecraft.world.item.ShieldItem;
    }

    @Override
    public void animateHurt(float yaw) {
        if (!this.isBlocking()) {
            super.animateHurt(yaw);
        }
    }

    @Override
    public boolean isDamageSourceBlocked(DamageSource source) {
        if (!this.isBlocking()) return false;
        if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow arr
                && arr.getPierceLevel() > 0) return false;
        return true;
    }

    @Override
    protected void blockUsingShield(LivingEntity attacker) {
        // Intentionnellement vide : vanilla appellerait attacker.blockedByShield(this)
        // qui finit par appeler this.stopUsingItem() et coupe notre shield
    }

    @Override
    public boolean removeWhenFarAway(double distToClosestPlayer) {
        return false;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }
}
