package org.furranystudio.thefakeplayer.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import org.furranystudio.thefakeplayer.Entity.Menu.FakePlayerInventoryMenu;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.furranystudio.thefakeplayer.Entity.Build.BaseRecord;
import org.furranystudio.thefakeplayer.Entity.Build.ConstructionTask;
import org.furranystudio.thefakeplayer.Entity.Build.HardcodedShelterBuilder;
import org.furranystudio.thefakeplayer.Entity.Build.StructureBlueprint;
import org.furranystudio.thefakeplayer.Entity.Build.StructureRegistry;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;
import org.furranystudio.thefakeplayer.Entity.Goals.FakePlayerFishGoal;
import org.furranystudio.thefakeplayer.network.BuildVisualizePacket;
import org.furranystudio.thefakeplayer.network.NetworkHandler;

@Mod.EventBusSubscriber
public class FakePlayerCommands {

    // Tracks which players currently have the build visualizer active
    private static final java.util.Set<java.util.UUID> visualizingPlayers = new java.util.HashSet<>();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        CommandBuildContext buildContext = event.getBuildContext();

        dispatcher.register(
            Commands.literal("spawnfakeplayer")
                .executes(context -> spawnFakePlayer(context.getSource(), null))
                .then(Commands.argument("username", StringArgumentType.word())
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> spawnFakePlayer(context.getSource(), StringArgumentType.getString(context, "username")))
                )
        );

        dispatcher.register(
            Commands.literal("stopfakeplayer")
                .executes(context -> {
                    FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                    if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                    fp.remove(Entity.RemovalReason.DISCARDED);
                    context.getSource().sendSuccess(() -> Component.translatable("thefakeplayer.command.removed"), false);
                    return Command.SINGLE_SUCCESS;
                })
        );

        dispatcher.register(
            Commands.literal("fakeplayer")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.translatable("thefakeplayer.command.help"), false);
                    return Command.SINGLE_SUCCESS;
                })

                .then(Commands.literal("heal")
                    .executes(context -> {
                        FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                        if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                        fp.setHealth(fp.getMaxHealth());
                        context.getSource().sendSuccess(() -> Component.translatable("thefakeplayer.command.healed", fp.getMaxHealth()), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )

                .then(Commands.literal("tp")
                    .executes(context -> {
                        FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                        if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                        Player player = context.getSource().getPlayerOrException();
                        fp.teleportTo(player.getX(), player.getY(), player.getZ());
                        context.getSource().sendSuccess(() -> Component.translatable("thefakeplayer.command.teleported"), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )

                .then(Commands.literal("god")
                    .executes(context -> {
                        FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                        if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                        fp.godMode = !fp.godMode;
                        context.getSource().sendSuccess(() -> Component.translatable(fp.godMode ? "thefakeplayer.command.god_enabled" : "thefakeplayer.command.god_disabled"), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )

                .then(Commands.literal("debug")
                    .executes(context -> {
                        FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                        if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));

                        String targetInfo = fp.getTarget() != null
                            ? fp.getTarget().getName().getString() + " (" + fp.getTarget().getType().toShortString() + ")"
                            : "none";
                        int itemCount = 0;
                        for (int i = 0; i < fp.getInventory().getContainerSize(); i++) {
                            if (!fp.getInventory().getItem(i).isEmpty()) itemCount++;
                        }

                        String msg = "=== FakePlayer Debug: " + fp.getName().getString() + " ===" +
                            "\nHealth: " + fp.getHealth() + " / " + fp.getMaxHealth() +
                            "\nGod mode: " + fp.godMode +
                            "\nAI frozen: " + fp.isNoAi() +
                            "\nCan pick up loot: " + fp.canPickUpLoot() +
                            "\nTarget: " + targetInfo +
                            "\nBlocking: " + fp.isBlocking() +
                            "\nInventory: " + itemCount + " item(s)" +
                            "\n--- Equipment ---" +
                            "\nMainhand: " + (fp.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() ? "empty" : fp.getItemBySlot(EquipmentSlot.MAINHAND).getHoverName().getString()) +
                            "\nOffhand:  " + (fp.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty() ? "empty" : fp.getItemBySlot(EquipmentSlot.OFFHAND).getHoverName().getString()) +
                            "\nHead:     " + (fp.getItemBySlot(EquipmentSlot.HEAD).isEmpty() ? "empty" : fp.getItemBySlot(EquipmentSlot.HEAD).getHoverName().getString()) +
                            "\nChest:    " + (fp.getItemBySlot(EquipmentSlot.CHEST).isEmpty() ? "empty" : fp.getItemBySlot(EquipmentSlot.CHEST).getHoverName().getString()) +
                            "\nLegs:     " + (fp.getItemBySlot(EquipmentSlot.LEGS).isEmpty() ? "empty" : fp.getItemBySlot(EquipmentSlot.LEGS).getHoverName().getString()) +
                            "\nFeet:     " + (fp.getItemBySlot(EquipmentSlot.FEET).isEmpty() ? "empty" : fp.getItemBySlot(EquipmentSlot.FEET).getHoverName().getString());

                        context.getSource().sendSuccess(() -> Component.literal(msg), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )

                .then(Commands.literal("inventory")
                    .executes(context -> {
                        FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                        if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));

                        StringBuilder sb = new StringBuilder("=== FakePlayer Inventory: " + fp.getName().getString() + " ===");
                        int count = 0;
                        for (int i = 0; i < fp.getInventory().getContainerSize(); i++) {
                            ItemStack stack = fp.getInventory().getItem(i);
                            if (!stack.isEmpty()) {
                                sb.append("\n[").append(i).append("] ").append(stack.getHoverName().getString()).append(" x").append(stack.getCount());
                                count++;
                            }
                        }
                        if (count == 0) sb.append("\nEmpty.");

                        context.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )

                .then(Commands.literal("clearinventory")
                    .executes(context -> {
                        FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                        if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                        fp.getInventory().clearContent();
                        context.getSource().sendSuccess(() -> Component.translatable("thefakeplayer.command.inventory_cleared"), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )

                .then(Commands.literal("cleartarget")
                    .executes(context -> {
                        FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                        if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                        fp.setTarget(null);
                        fp.suppressTargetingTicks = 100;
                        context.getSource().sendSuccess(() -> Component.translatable("thefakeplayer.command.target_cleared"), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )

                .then(Commands.literal("freeze")
                    .executes(context -> {
                        FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                        if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                        fp.setNoAi(true);
                        context.getSource().sendSuccess(() -> Component.translatable("thefakeplayer.command.ai_frozen"), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )

                .then(Commands.literal("unfreeze")
                    .executes(context -> {
                        FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                        if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                        fp.setNoAi(false);
                        context.getSource().sendSuccess(() -> Component.translatable("thefakeplayer.command.ai_resumed"), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )

                .then(Commands.literal("goals")
                    .executes(context -> {
                        FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                        if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));

                        StringBuilder sb = new StringBuilder("=== FakePlayer Goals: " + fp.getName().getString() + " ===");
                        fp.getGoalSelector().getAvailableGoals().stream()
                            .sorted((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                            .forEach(wrapped -> sb.append("\n")
                                .append(wrapped.isRunning() ? "[ACTIVE] " : "[idle]   ")
                                .append("P").append(wrapped.getPriority()).append(" ")
                                .append(wrapped.getGoal().getClass().getSimpleName()));
                        sb.append("\n--- Target Goals ---");
                        fp.getTargetSelector().getAvailableGoals().stream()
                            .sorted((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                            .forEach(wrapped -> sb.append("\n")
                                .append(wrapped.isRunning() ? "[ACTIVE] " : "[idle]   ")
                                .append("P").append(wrapped.getPriority()).append(" ")
                                .append(wrapped.getGoal().getClass().getSimpleName()));

                        context.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )

                .then(Commands.literal("give")
                    .then(Commands.argument("item", ItemArgument.item(buildContext))
                        .executes(context -> {
                            FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                            if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                            ItemStack stack = ItemArgument.getItem(context, "item").createItemStack(1, false);
                            ItemStack leftover = fp.getInventory().addItem(stack);
                            if (leftover.isEmpty()) {
                                context.getSource().sendSuccess(() -> Component.translatable("thefakeplayer.command.gave_item", stack.getHoverName()), false);
                            } else {
                                context.getSource().sendSuccess(() -> Component.translatable("thefakeplayer.command.inventory_full"), false);
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )

                .then(Commands.literal("inventoryui")
                    .executes(context -> {
                        FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                        if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                        Player player = context.getSource().getPlayerOrException();
                        if (!(player instanceof ServerPlayer serverPlayer)) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_player"));
                        serverPlayer.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> new FakePlayerInventoryMenu(id, inv, fp),
                            fp.getName()
                        ));
                        return Command.SINGLE_SUCCESS;
                    })
                )

                .then(Commands.literal("setgoal")
                    .then(Commands.argument("goal", StringArgumentType.word())
                        .executes(context -> {
                            FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                            if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                            String arg = StringArgumentType.getString(context, "goal").toLowerCase();

                            if (arg.equals("none") || arg.equals("clear")) {
                                fp.getGoalSelector().getAvailableGoals().stream()
                                    .filter(WrappedGoal::isRunning)
                                    .forEach(WrappedGoal::stop);
                                context.getSource().sendSuccess(() -> Component.translatable("thefakeplayer.command.goals_stopped"), false);
                                return Command.SINGLE_SUCCESS;
                            }

                            WrappedGoal match = fp.getGoalSelector().getAvailableGoals().stream()
                                .filter(w -> w.getGoal().getClass().getSimpleName().toLowerCase().contains(arg))
                                .findFirst()
                                .orElse(null);

                            if (match == null) {
                                StringBuilder available = new StringBuilder();
                                fp.getGoalSelector().getAvailableGoals().forEach(w -> {
                                    if (available.length() > 0) available.append(", ");
                                    available.append(w.getGoal().getClass().getSimpleName());
                                });
                                return error(context.getSource(), Component.translatable("thefakeplayer.command.no_goal_match", arg, available.toString()));
                            }

                            // Initialize internal state, bypassing cooldown and goal-specific guards
                            if (match.getGoal() instanceof FakePlayerFishGoal fishGoal) {
                                // forceInit bypasses target check; ignore return — let start() catch real failures
                                fishGoal.forceInit();
                            } else {
                                resetGoalCooldown(match.getGoal());
                                boolean ready = match.getGoal().canUse();
                                if (!ready) {
                                    return error(context.getSource(), Component.translatable("thefakeplayer.command.goal_not_met"));
                                }
                            }
                            // Stop only goals that conflict on the same flags (MOVE, LOOK, etc.)
                            java.util.EnumSet<net.minecraft.world.entity.ai.goal.Goal.Flag> flags = match.getGoal().getFlags();
                            fp.getGoalSelector().getAvailableGoals().stream()
                                .filter(w -> w.isRunning() && w.getGoal().getFlags().stream().anyMatch(flags::contains))
                                .forEach(WrappedGoal::stop);
                            try {
                                match.start();
                            } catch (Exception e) {
                                return error(context.getSource(), Component.translatable("thefakeplayer.command.goal_start_failed", e.getMessage()));
                            }
                            String name = match.getGoal().getClass().getSimpleName();
                            context.getSource().sendSuccess(() -> Component.translatable("thefakeplayer.command.goal_started", name), false);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )

                // ── /fakeplayer build ────────────────────────────────────────────────
                .then(Commands.literal("build")
                    .executes(context -> {
                        FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                        if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                        return cmdBuildStatus(context.getSource(), fp);
                    })
                    .then(Commands.literal("status")
                        .executes(context -> {
                            FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                            if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                            return cmdBuildStatus(context.getSource(), fp);
                        })
                    )
                    .then(Commands.literal("cancel")
                        .executes(context -> {
                            FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                            if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                            ConstructionTask task = fp.getActiveTask();
                            if (task == null) return error(context.getSource(), Component.literal("No active construction task."));
                            task.setAbandoned(true);
                            fp.setActiveTask(null);
                            context.getSource().sendSuccess(() -> Component.literal("Construction cancelled."), false);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                    .then(Commands.literal("resume")
                        .executes(context -> {
                            FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                            if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                            ConstructionTask task = fp.getActiveTask();
                            if (task == null) return error(context.getSource(), Component.literal("No construction task to resume."));
                            task.setAbandoned(false);
                            context.getSource().sendSuccess(() -> Component.literal("Construction task unmarked as abandoned — will resume on next goal tick."), false);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                    .then(Commands.literal("tp")
                        .executes(context -> {
                            FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                            if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                            ConstructionTask task = fp.getActiveTask();
                            if (task == null) return error(context.getSource(), Component.literal("No active construction task."));
                            Player player = context.getSource().getPlayerOrException();
                            net.minecraft.core.BlockPos o = task.getOrigin();
                            player.teleportTo(o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
                            context.getSource().sendSuccess(() -> Component.literal("Teleported to task origin " + o.toShortString()), false);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                    .then(Commands.literal("random")
                        .executes(context -> {
                            FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                            if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                            StructureBlueprint bp = StructureRegistry.get().getRandomBlueprint(fp.getRandom());
                            if (bp == null) return error(context.getSource(), Component.literal("No blueprints loaded in registry."));
                            fp.setActiveTask(new ConstructionTask(fp.blockPosition(), bp.getId()));
                            context.getSource().sendSuccess(() -> Component.literal("Queued random build: " + bp.getId()), false);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                    .then(Commands.literal("hardcoded")
                        .executes(context -> {
                            FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                            if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                            net.minecraft.world.level.block.Block mat = HardcodedShelterBuilder.chooseMaterial(fp.getInventory());
                            if (mat == null) return error(context.getSource(), Component.literal("Not enough building materials in inventory."));
                            StructureBlueprint bp = HardcodedShelterBuilder.build(mat);
                            StructureRegistry.get().registerRuntime(bp);
                            fp.setActiveTask(new ConstructionTask(fp.blockPosition(), bp.getId()));
                            context.getSource().sendSuccess(() -> Component.literal("Queued hardcoded shelter build at " + fp.blockPosition().toShortString()), false);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                    .then(Commands.literal("visualize")
                        .executes(context -> {
                            FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                            if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                            Player player = context.getSource().getPlayerOrException();
                            if (!(player instanceof ServerPlayer serverPlayer))
                                return error(context.getSource(), Component.literal("Must be run by a player."));
                            return cmdBuildVisualize(context.getSource(), fp, serverPlayer);
                        })
                    )
                    .then(Commands.argument("id", StringArgumentType.word())
                        .executes(context -> {
                            FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                            if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                            String id = StringArgumentType.getString(context, "id");
                            StructureBlueprint bp = StructureRegistry.get().getBlueprintById(id);
                            if (bp == null) return error(context.getSource(), Component.literal("Blueprint not found: " + id + ". Use /fakeplayer schematics list to see available ones."));
                            fp.setActiveTask(new ConstructionTask(fp.blockPosition(), bp.getId()));
                            context.getSource().sendSuccess(() -> Component.literal("Queued build: " + id + " at " + fp.blockPosition().toShortString()), false);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )

                // ── /fakeplayer bases ─────────────────────────────────────────────────
                .then(Commands.literal("bases")
                    .then(Commands.literal("list")
                        .executes(context -> {
                            FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                            if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                            java.util.List<BaseRecord> bases = fp.getKnownBases();
                            if (bases.isEmpty()) { context.getSource().sendSuccess(() -> Component.literal("No known bases."), false); return Command.SINGLE_SUCCESS; }
                            StringBuilder sb = new StringBuilder("=== Known Bases (" + bases.size() + ") ===");
                            for (int i = 0; i < bases.size(); i++) {
                                BaseRecord b = bases.get(i);
                                sb.append("\n[").append(i).append("] center=").append(b.getCenter().toShortString())
                                  .append(" bed=").append(b.getBedPos() != null ? b.getBedPos().toShortString() : "none")
                                  .append(" complete=").append(b.isComplete());
                            }
                            context.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                    .then(Commands.literal("clear")
                        .executes(context -> {
                            FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                            if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                            fp.getKnownBases().clear();
                            context.getSource().sendSuccess(() -> Component.literal("All known bases cleared."), false);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                    .then(Commands.literal("nearest")
                        .executes(context -> {
                            FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                            if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                            BaseRecord nearest = fp.getNearestBase();
                            if (nearest == null) return error(context.getSource(), Component.literal("No known bases."));
                            Player player = context.getSource().getPlayerOrException();
                            net.minecraft.core.BlockPos c = nearest.getCenter();
                            player.teleportTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
                            context.getSource().sendSuccess(() -> Component.literal("Teleported to nearest base at " + c.toShortString()), false);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                    .then(Commands.literal("add")
                        .executes(context -> {
                            FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                            if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                            BaseRecord base = new BaseRecord(fp.blockPosition());
                            base.setComplete(true);
                            fp.addBase(base);
                            context.getSource().sendSuccess(() -> Component.literal("Added base at " + fp.blockPosition().toShortString()), false);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )

                // ── /fakeplayer schematics ────────────────────────────────────────────
                .then(Commands.literal("schematics")
                    .then(Commands.literal("list")
                        .executes(context -> {
                            java.util.Map<String, StructureBlueprint> all = StructureRegistry.get().getAll();
                            if (all.isEmpty()) { context.getSource().sendSuccess(() -> Component.literal("No blueprints loaded."), false); return Command.SINGLE_SUCCESS; }
                            StringBuilder sb = new StringBuilder("=== Loaded Blueprints (" + all.size() + ") ===");
                            for (StructureBlueprint bp : all.values()) {
                                sb.append("\n- ").append(bp.getId())
                                  .append(" (").append(bp.getWidth()).append("x").append(bp.getHeight()).append("x").append(bp.getDepth())
                                  .append(", ").append(bp.getBlocks().size()).append(" blocks)");
                            }
                            context.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                    .then(Commands.literal("reload")
                        .executes(context -> {
                            StructureRegistry.get().reload();
                            int count = StructureRegistry.get().getAll().size();
                            context.getSource().sendSuccess(() -> Component.literal("Schematics reloaded. " + count + " blueprint(s) loaded."), false);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )

                // ── /fakeplayer bridge ────────────────────────────────────────────────
                .then(Commands.literal("bridge")
                    .executes(context -> {
                        FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                        if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                        fp.getGoalSelector().getAvailableGoals().stream()
                            .filter(w -> w.getGoal() instanceof org.furranystudio.thefakeplayer.Entity.Goals.FakePlayerBridgeGoal)
                            .findFirst()
                            .ifPresentOrElse(w -> {
                                w.getGoal().getFlags().forEach(f -> fp.getGoalSelector().getAvailableGoals().stream()
                                    .filter(o -> o.isRunning() && o.getGoal().getFlags().contains(f))
                                    .forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop));
                                w.start();
                                context.getSource().sendSuccess(() -> Component.literal("BridgeGoal force-started."), false);
                            }, () -> context.getSource().sendFailure(Component.literal("BridgeGoal not found.")));
                        return Command.SINGLE_SUCCESS;
                    })
                )

                .then(Commands.literal("target")
                    .then(Commands.argument("entity", EntityArgument.entity())
                        .executes(context -> {
                            FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                            if (fp == null) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_found"));
                            Entity target = EntityArgument.getEntity(context, "entity");
                            if (!(target instanceof LivingEntity living)) return error(context.getSource(), Component.translatable("thefakeplayer.command.not_living"));
                            fp.setTarget(living);
                            context.getSource().sendSuccess(() -> Component.translatable("thefakeplayer.command.targeting", target.getName()), false);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
        );
    }

    private static int cmdBuildStatus(CommandSourceStack source, FakePlayerEntity fp) {
        ConstructionTask task = fp.getActiveTask();
        if (task == null) {
            source.sendSuccess(() -> Component.literal("No active construction task."), false);
            return Command.SINGLE_SUCCESS;
        }
        StructureBlueprint bp = StructureRegistry.get().getBlueprintById(task.getBlueprintId());
        int total = bp != null ? bp.getBlocks().size() : -1;
        int placed = task.getPlacedIndices().size();
        String progress = total >= 0 ? placed + "/" + total : placed + "/? (blueprint not in registry)";
        String msg = "=== Build Status ===" +
            "\nBlueprint : " + task.getBlueprintId() +
            "\nOrigin    : " + task.getOrigin().toShortString() +
            "\nProgress  : " + progress + " blocks" +
            "\nAbandoned : " + task.isAbandoned();
        source.sendSuccess(() -> Component.literal(msg), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int cmdBuildVisualize(CommandSourceStack source, FakePlayerEntity fp, ServerPlayer serverPlayer) {
        java.util.UUID uid = serverPlayer.getUUID();
        if (visualizingPlayers.remove(uid)) {
            NetworkHandler.sendVisualize(serverPlayer, new BuildVisualizePacket(false, java.util.List.of(), java.util.List.of()));
            source.sendSuccess(() -> Component.literal("Build visualizer disabled."), false);
            return Command.SINGLE_SUCCESS;
        }

        ConstructionTask task = fp.getActiveTask();
        if (task == null) return error(source, Component.literal("No active construction task to visualize."));
        StructureBlueprint bp = StructureRegistry.get().getBlueprintById(task.getBlueprintId());
        if (bp == null) return error(source, Component.literal("Blueprint not in registry: " + task.getBlueprintId()));

        java.util.List<net.minecraft.core.BlockPos> pending = new java.util.ArrayList<>();
        java.util.List<net.minecraft.core.BlockPos> missing = new java.util.ArrayList<>();
        net.minecraft.core.BlockPos origin = task.getOrigin();
        net.minecraft.world.SimpleContainer inv = fp.getInventory();

        for (int i = 0; i < bp.getBlocks().size(); i++) {
            if (task.getPlacedIndices().contains(i)) continue;
            StructureBlueprint.PlacementEntry entry = bp.getBlocks().get(i);
            net.minecraft.core.BlockPos worldPos = origin.offset(entry.offset());
            net.minecraft.world.item.Item needed = visualizeBlockToItem(entry.state().getBlock());
            boolean has = needed != net.minecraft.world.item.Items.AIR && visualizeHasItem(inv, needed);
            if (has) pending.add(worldPos); else missing.add(worldPos);
        }

        visualizingPlayers.add(uid);
        final int p = pending.size(), m = missing.size();
        NetworkHandler.sendVisualize(serverPlayer, new BuildVisualizePacket(true, pending, missing));
        source.sendSuccess(() -> Component.literal("Build visualizer enabled: " + p + " to place (white), " + m + " missing (red). Run again to toggle off."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static net.minecraft.world.item.Item visualizeBlockToItem(net.minecraft.world.level.block.Block block) {
        if (block == net.minecraft.world.level.block.Blocks.WALL_TORCH) return net.minecraft.world.item.Items.TORCH;
        if (block == net.minecraft.world.level.block.Blocks.SOUL_WALL_TORCH) return net.minecraft.world.item.Items.SOUL_TORCH;
        return block.asItem();
    }

    private static boolean visualizeHasItem(net.minecraft.world.SimpleContainer inv, net.minecraft.world.item.Item item) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.getItem() == item) return true;
        }
        return false;
    }

    private static FakePlayerEntity findFakePlayer(ServerLevel level) {
        for (Entity entity : level.getEntities().getAll()) {
            if (entity instanceof FakePlayerEntity fp) return fp;
        }
        return null;
    }

    private static int spawnFakePlayer(CommandSourceStack source, String username) {
        ServerLevel world = source.getLevel();
        for (Entity entity : world.getEntities().getAll()) {
            if (entity instanceof FakePlayerEntity fp) {
                source.sendSuccess(() -> Component.translatable("thefakeplayer.command.already_here", fp.getName()), false);
                return Command.SINGLE_SUCCESS;
            }
        }
        try {
            Player player = source.getPlayerOrException();
            FakePlayerEntity fakePlayer = (username == null || username.isBlank())
                ? new FakePlayerEntity(world, player.getX(), player.getY(), player.getZ())
                : new FakePlayerEntity(world, player.getX(), player.getY(), player.getZ(), username);
            fakePlayer.setPos(player.getX(), player.getY(), player.getZ());
            world.addFreshEntity(fakePlayer);
        } catch (Exception e) {
            source.sendFailure(Component.translatable("thefakeplayer.command.spawn_failed", e.getMessage()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void resetGoalCooldown(net.minecraft.world.entity.ai.goal.Goal goal) {
        try {
            java.lang.reflect.Field f = goal.getClass().getDeclaredField("cooldown");
            f.setAccessible(true);
            f.setInt(goal, 0);
        } catch (Exception ignored) {}
    }

    private static int error(CommandSourceStack source, Component message) {
        source.sendFailure(message);
        return 0;
    }
}
