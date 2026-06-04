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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

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
                    if (fp == null) return error(context.getSource(), "No FakePlayer found.");
                    fp.remove(Entity.RemovalReason.DISCARDED);
                    context.getSource().sendSuccess(() -> Component.literal("FakePlayer removed."), false);
                    return Command.SINGLE_SUCCESS;
                })
        );

        dispatcher.register(
            Commands.literal("fakeplayer")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(
                        "FakePlayer commands: heal, tp, god, debug, inventory, clearinventory, cleartarget, freeze, unfreeze, goals, give <item>, target <entity>"
                    ), false);
                    return Command.SINGLE_SUCCESS;
                })

                .then(Commands.literal("heal")
                    .executes(context -> {
                        FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                        if (fp == null) return error(context.getSource(), "No FakePlayer found.");
                        fp.setHealth(fp.getMaxHealth());
                        context.getSource().sendSuccess(() -> Component.literal("FakePlayer healed to " + fp.getMaxHealth() + " HP."), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )

                .then(Commands.literal("tp")
                    .executes(context -> {
                        FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                        if (fp == null) return error(context.getSource(), "No FakePlayer found.");
                        Player player = context.getSource().getPlayerOrException();
                        fp.teleportTo(player.getX(), player.getY(), player.getZ());
                        context.getSource().sendSuccess(() -> Component.literal("FakePlayer teleported to your position."), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )

                .then(Commands.literal("god")
                    .executes(context -> {
                        FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                        if (fp == null) return error(context.getSource(), "No FakePlayer found.");
                        fp.godMode = !fp.godMode;
                        context.getSource().sendSuccess(() -> Component.literal("God mode " + (fp.godMode ? "enabled" : "disabled") + "."), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )

                .then(Commands.literal("debug")
                    .executes(context -> {
                        FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                        if (fp == null) return error(context.getSource(), "No FakePlayer found.");

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
                            "\nInventory: " + itemCount + " item(s)";

                        context.getSource().sendSuccess(() -> Component.literal(msg), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )

                .then(Commands.literal("inventory")
                    .executes(context -> {
                        FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                        if (fp == null) return error(context.getSource(), "No FakePlayer found.");

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
                        if (fp == null) return error(context.getSource(), "No FakePlayer found.");
                        fp.getInventory().clearContent();
                        context.getSource().sendSuccess(() -> Component.literal("FakePlayer inventory cleared."), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )

                .then(Commands.literal("cleartarget")
                    .executes(context -> {
                        FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                        if (fp == null) return error(context.getSource(), "No FakePlayer found.");
                        fp.setTarget(null);
                        fp.suppressTargetingTicks = 100;
                        context.getSource().sendSuccess(() -> Component.literal("FakePlayer target cleared (suppressed for 5s)."), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )

                .then(Commands.literal("freeze")
                    .executes(context -> {
                        FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                        if (fp == null) return error(context.getSource(), "No FakePlayer found.");
                        fp.setNoAi(true);
                        context.getSource().sendSuccess(() -> Component.literal("FakePlayer AI frozen."), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )

                .then(Commands.literal("unfreeze")
                    .executes(context -> {
                        FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                        if (fp == null) return error(context.getSource(), "No FakePlayer found.");
                        fp.setNoAi(false);
                        context.getSource().sendSuccess(() -> Component.literal("FakePlayer AI resumed."), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )

                .then(Commands.literal("goals")
                    .executes(context -> {
                        FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                        if (fp == null) return error(context.getSource(), "No FakePlayer found.");

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
                            if (fp == null) return error(context.getSource(), "No FakePlayer found.");
                            ItemStack stack = ItemArgument.getItem(context, "item").createItemStack(1, false);
                            ItemStack leftover = fp.getInventory().addItem(stack);
                            if (leftover.isEmpty()) {
                                context.getSource().sendSuccess(() -> Component.literal("Gave " + stack.getHoverName().getString() + " to FakePlayer."), false);
                            } else {
                                context.getSource().sendSuccess(() -> Component.literal("Inventory full, item could not be added."), false);
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )

                .then(Commands.literal("inventoryui")
                    .executes(context -> {
                        FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                        if (fp == null) return error(context.getSource(), "No FakePlayer found.");
                        Player player = context.getSource().getPlayerOrException();
                        if (!(player instanceof ServerPlayer serverPlayer)) return error(context.getSource(), "Must be run by a player.");
                        serverPlayer.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> new FakePlayerInventoryMenu(id, inv, fp),
                            fp.getName()
                        ));
                        return Command.SINGLE_SUCCESS;
                    })
                )

                .then(Commands.literal("target")
                    .then(Commands.argument("entity", EntityArgument.entity())
                        .executes(context -> {
                            FakePlayerEntity fp = findFakePlayer(context.getSource().getLevel());
                            if (fp == null) return error(context.getSource(), "No FakePlayer found.");
                            Entity target = EntityArgument.getEntity(context, "entity");
                            if (!(target instanceof LivingEntity living)) return error(context.getSource(), "Target must be a living entity.");
                            fp.setTarget(living);
                            context.getSource().sendSuccess(() -> Component.literal("FakePlayer is now targeting " + target.getName().getString() + "."), false);
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
                source.sendSuccess(() -> Component.literal(fp.getName().getString() + " is already here."), false);
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
            source.sendFailure(Component.literal("Failed to spawn FakePlayer: " + e.getMessage()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int error(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }
}
