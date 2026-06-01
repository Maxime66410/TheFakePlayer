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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import org.furranystudio.thefakeplayer.Entity.Goals.FakePlayerEatGoal;
import org.furranystudio.thefakeplayer.Entity.Goals.FakePlayerHarvestGoal;
import org.furranystudio.thefakeplayer.Entity.Goals.FakePlayerMineGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.PathfinderMob;
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

    // Tab list / chat
    private boolean hasTabListEntry = false;
    private boolean initialized = false;
    private boolean profileReady = false; // true quand nom+skin sont chargés (thread terminé)
    private int chatTimer = 20 * 60 * 3; // premier message après 3 minutes

    // Skin Mojang brut (base64 value + signature pour GameProfile)
    private String skinTextureValue = null;
    private String skinTextureSignature = null;
    private String skinUrl = "";

    // EntityData pour synchroniser l'URL du skin serveur → clients automatiquement
    private static final EntityDataAccessor<String> SKIN_URL =
        SynchedEntityData.defineId(FakePlayerEntity.class, EntityDataSerializers.STRING);
    private String lastSkinUrl = "";

    // EntityData pour synchroniser les animations serveur → clients
    private static final EntityDataAccessor<Integer> EAT_ANIM_TICK =
        SynchedEntityData.defineId(FakePlayerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SWING_ANIM_TICK =
        SynchedEntityData.defineId(FakePlayerEntity.class, EntityDataSerializers.INT);

    // Interpolation côté client pour lisser l'animation de swing (60fps)
    public float oSwingAnimFrac = 0.0F; // valeur tick précédent
    public float swingAnimFrac = 0.0F;  // valeur tick courant

    private static final String[] CHAT_MESSAGES = {
        "lol",
        "gg",
        "quelqu'un est là ?",
        "nice base",
        "t'es en train de construire quoi ?",
        "j'ai trouvé des diamants",
        "brb",
        "back",
        "ce seed est ouf",
        "...",
        "je mine un peu",
        "on se fait une mine ?",
        "j'ai faim lol",
        "attention creeper derrière toi",
        "je vais explorer un peu",
        "ok je craft",
        "t'as du bois ?",
        "j'ai failli mourir",
        "bien joué",
        "on est à combien de distance du spawn ?"
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
        UpdateEntityProfile(); // Doit être après super() — les field initializers s'exécutent après super() et écraseraient skinTextureValue
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

    // Accesseurs synchro animation
    public int getEatAnimTick() { return this.entityData.get(EAT_ANIM_TICK); }
    public void setEatAnimTick(int tick) { this.entityData.set(EAT_ANIM_TICK, tick); }
    public int getSwingAnimTick() { return this.entityData.get(SWING_ANIM_TICK); }

    /** Déclenche l'animation de swing (harvest, attaque…). Durée fixe 10 ticks. */
    public void triggerSwingAnim() {
        if (!this.level().isClientSide()) {
            this.entityData.set(SWING_ANIM_TICK, 10);
        }
    }

    // Suppression : retire du tab list + message de déconnexion
    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide() && hasTabListEntry) {
            removeFromTabList();
            hasTabListEntry = false;
            broadcastLeaveMessage();
        }
        super.remove(reason);
    }

    // Construit le packet tab list pour ce fake player via réflexion
    // Le constructeur public ne prend que Collection<ServerPlayer>, pas List<Entry>
    @SuppressWarnings("unchecked")
    private ClientboundPlayerInfoUpdatePacket buildTabListPacket() {
        try {
            GameProfile profile = new GameProfile(this.getUUID(), this.getName().getString());

            // Ajouter la texture Mojang au profil — signature obligatoire pour que le client l'accepte
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

            // Le constructeur public exige Collection<ServerPlayer> — on passe une liste vide
            // via cast non vérifié (safe à runtime grâce à l'erasure Java)
            Collection<ServerPlayer> emptyPlayers = (Collection<ServerPlayer>)(Collection<?>) Collections.emptyList();
            ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(actions, emptyPlayers);

            // On remplace le champ entries avec notre entrée custom (nom officiel Mojang)
            java.lang.reflect.Field entriesField = ClientboundPlayerInfoUpdatePacket.class.getDeclaredField("entries");
            entriesField.setAccessible(true);
            entriesField.set(packet, List.of(entry));

            return packet;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to build FakePlayer tab list packet", e);
        }
    }

    // Envoie le packet d'ajout dans le tab list à tous les joueurs connectés
    public void addToTabList() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        ClientboundPlayerInfoUpdatePacket packet = buildTabListPacket();
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }

    // Envoie l'entrée tab list à un joueur spécifique (pour les joueurs qui rejoignent après)
    public void sendTabListEntryTo(ServerPlayer player) {
        player.connection.send(buildTabListPacket());
    }

    // Envoie le packet de retrait du tab list à tous les joueurs connectés
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

    // Broadcast "joined the game" à tous les joueurs
    private void broadcastJoinMessage() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        serverLevel.getServer().getPlayerList().broadcastSystemMessage(
            Component.literal("§e" + this.getName().getString() + " joined the game"), false
        );
    }

    // Broadcast "left the game" à tous les joueurs
    private void broadcastLeaveMessage() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        serverLevel.getServer().getPlayerList().broadcastSystemMessage(
            Component.literal("§e" + this.getName().getString() + " left the game"), false
        );
    }

    // Envoie un message aléatoire dans le chat au nom du fake player
    private void sendRandomChatMessage() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        String msg = CHAT_MESSAGES[this.random.nextInt(CHAT_MESSAGES.length)];
        serverLevel.getServer().getPlayerList().broadcastSystemMessage(
            Component.literal("<" + this.getName().getString() + "> " + msg), false
        );
        // Reset timer entre 3 et 10 minutes
        chatTimer = 20 * 60 * (3 + this.random.nextInt(8));
    }

    // Methods - Entity for FakePlayerEntity
    @Override
    protected void registerGoals() {

        // Add goals to the entity
        this.goalSelector.addGoal(0, new FloatGoal(this)); // Permet de flotter dans l'eau
        this.goalSelector.addGoal(1, new FakePlayerEatGoal(this));
        this.goalSelector.addGoal(4, new FakePlayerHarvestGoal(this));
        this.goalSelector.addGoal(6, new FakePlayerMineGoal(this));
        this.goalSelector.addGoal(7, new RandomStrollGoal(this, 1.0D)); // Permet de se déplacer aléatoirement
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F)); // Permet de regarder le joueur
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true)); // Permet d'attaquer le joueur
        this.goalSelector.addGoal(3, new BreakDoorGoal(this, (HARD) -> {
            return true;
        })); // Permet de casser les portes
        this.goalSelector.addGoal(2, new OpenDoorGoal(this, true)); // Permet d'ouvrir les portes
        this.goalSelector.addGoal(5, new PanicGoal(this, 1.25D)); // Permet de paniquer
        this.goalSelector.addGoal(2, new EatBlockGoal(this)); // Permet de manger des blocs
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector.addGoal(6, new ResetUniversalAngerTargetGoal<>(this, false));
        this.goalSelector.addGoal(3, new MoveThroughVillageGoal(this, 1.0, true, 4, this::canBreakDoors)); // Permet de se déplacer dans le village

        this.setCanPickUpLoot(true);
    }

    // Update the entity's profile — HTTP sur thread séparé pour ne pas bloquer le serveur
    private void UpdateEntityProfile()
    {
        // Pas de nom affiché tant que le profil n'est pas chargé
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
                        // Si tick() n'a pas encore ajouté au tab, on le fait maintenant
                        if (!hasTabListEntry) { addToTabList(); hasTabListEntry = true; broadcastJoinMessage(); }
                    });
                    return;
                }
                // Fallback : aucun nom valide en 5 tentatives
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
        // Nom connu immédiatement → profileReady = true pour que tick() puisse broadcaster le join tout de suite
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
                    // Si tick() n'a pas encore ajouté au tab (très rare), on le fait maintenant
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
        return customSkin != null ? customSkin : ResourceLocation.fromNamespaceAndPath(Thefakeplayer.MODID,"textures/entities/basefakeplayer.png");  // Skin par défaut si aucun personnalisé
    }

    // Set Custom Skin Location
    public void setCustomSkin(ResourceLocation skin) {
        this.customSkin = skin;

        // Mise à jour de la texture uniquement côté client (Minecraft.getInstance() n'existe pas côté serveur)
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

    // Retourne [skinUrl, base64Value, signature] depuis l'UUID Mojang, ou null si introuvable
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

    // Compat : retourne uniquement l'URL du skin (utilisé en interne)
    public static String getSkinURL(String uuid) throws IOException {
        String[] data = getSkinProperty(uuid);
        return data != null ? data[0] : null;
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

    // Applique les données de skin pré-fetchées (évite un double appel session server)
    public void applySkin(String playerName, String[] skinData) {
        try {
            skinUrl              = skinData[0];
            skinTextureValue     = skinData[1];
            skinTextureSignature = skinData[2];

            setCustomSkin(downloadAndSaveSkin(skinUrl, playerName));

            // Synchroniser l'URL du skin vers tous les clients via EntityData (MC vanilla)
            this.entityData.set(SKIN_URL, skinUrl);

            // Si déjà dans le tab list, remove + readd pour forcer le client à accepter le nouveau profil
            // (ADD_PLAYER pour un UUID existant est ignoré par le client)
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
            profileReady = true; // Profil restauré depuis NBT, pas besoin d'attendre le thread
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
            // "left the game" est géré dans remove() pour couvrir tous les cas
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

            // Interpolation swing : mémoriser l'ancienne valeur avant de mettre à jour
            oSwingAnimFrac = swingAnimFrac;
            swingAnimFrac = (10 - this.entityData.get(SWING_ANIM_TICK)) / 10.0F;

            // Appliquer le skin dès que l'URL est synchronisée depuis le serveur
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

        // Décrémenter le compteur de swing (2 par tick → 5 ticks total = 0.25s)
        if (!level().isClientSide()) {
            int swing = this.entityData.get(SWING_ANIM_TICK);
            if (swing > 0) {
                this.entityData.set(SWING_ANIM_TICK, Math.max(0, swing - 2));
            }
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
        boolean result = super.doHurtTarget(level, target);
        if (result) {
            triggerSwingAnim();
        }
        return result;
    }
}
