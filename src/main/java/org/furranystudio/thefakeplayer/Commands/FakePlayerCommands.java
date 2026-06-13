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
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;
import org.furranystudio.thefakeplayer.Entity.Goals.FakePlayerFishGoal;

@Mod.EventBusSubscriber
public class FakePlayerCommands {

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
