package org.furranystudio.thefakeplayer.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Mod.EventBusSubscriber
public class FakePlayerCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("fakeplayer") // the command to test the fake player
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal("§6[FakePlayer] §7Commande exécutée !"), false);
                            return Command.SINGLE_SUCCESS;
                        })
        );

        dispatcher.register(
                Commands.literal("spawnfakeplayer") // the command to spawn the fake player
                        .executes(context -> {

                            CommandSourceStack source = context.getSource();
                            ServerLevel world = source.getLevel();


                            // Verify if the FakePlayer is already here
                            AtomicBoolean bIsFakePlayerIsHere = new AtomicBoolean(false);
                            AtomicReference<String> fakePlayerName = new AtomicReference<>("TheFakePlayer");

                            LevelEntityGetter<Entity> entities = world.getEntities();
                            entities.getAll().forEach(entity -> {
                                if (entity instanceof FakePlayerEntity) {
                                    bIsFakePlayerIsHere.set(true);
                                    fakePlayerName.set(entity.getName().getString());
                                    return;
                                }
                            });

                            if (bIsFakePlayerIsHere.get()) {
                                source.sendSuccess(() -> Component.literal("§e" + fakePlayerName + " is already here"), false);
                                return Command.SINGLE_SUCCESS;
                            }


                            Player player = source.getPlayerOrException();

                            FakePlayerEntity fakePlayer = new FakePlayerEntity(world, player.getX(), player.getY(), player.getZ());

                            fakePlayer.setPos(player.getX(), player.getY(), player.getZ());

                            world.addFreshEntity(fakePlayer);

                            source.sendSuccess(() -> Component.literal("§e" + fakePlayer.getName().getString() + " joined the game"), false);

                            return Command.SINGLE_SUCCESS;
                        })
        );

        dispatcher.register(Commands.literal("spawnfakeplayer")
                .requires(source -> source.hasPermission(2)) // Nécessite un niveau d'opérateur
                .then(Commands.argument("username", StringArgumentType.word())
                        .executes(context -> {
                            String mode = StringArgumentType.getString(context, "username");
                            CommandSourceStack source = context.getSource();
                            ServerLevel world = source.getLevel();

                            // Verify if the FakePlayer is already here
                            AtomicBoolean bIsFakePlayerIsHere = new AtomicBoolean(false);
                            AtomicReference<String> fakePlayerName = new AtomicReference<>("TheFakePlayer");

                            LevelEntityGetter<Entity> entities = world.getEntities();
                            entities.getAll().forEach(entity -> {
                                if (entity instanceof FakePlayerEntity) {
                                    bIsFakePlayerIsHere.set(true);
                                    fakePlayerName.set(entity.getName().getString());
                                    return;
                                }
                            });

                            if (bIsFakePlayerIsHere.get()) {
                                source.sendSuccess(() -> Component.literal("§e" + fakePlayerName + " is already here"), false);
                                return Command.SINGLE_SUCCESS;
                            }


                            Player player = source.getPlayerOrException();

                            FakePlayerEntity fakePlayer;

                            if(mode.isBlank()) {
                                fakePlayer = new FakePlayerEntity(world, player.getX(), player.getY(), player.getZ());
                            } else {
                                fakePlayer = new FakePlayerEntity(world, player.getX(), player.getY(), player.getZ(), mode);
                            }

                            fakePlayer.setPos(player.getX(), player.getY(), player.getZ());

                            world.addFreshEntity(fakePlayer);

                            source.sendSuccess(() -> Component.literal("§e" + fakePlayer.getName().getString() + " joined the game"), false);

                            return Command.SINGLE_SUCCESS;
                        })
                )
        );

        dispatcher.register(
                Commands.literal("stopfakeplayer") // the command to stop the fake player
                        .executes(context -> {

                            CommandSourceStack source = context.getSource();
                            ServerLevel world = source.getLevel();

                            // Pick all entities NOT OPTIMIZED
                            LevelEntityGetter<Entity> entities = world.getEntities();
                            AtomicReference<String> fakePlayerName = new AtomicReference<>("TheFakePlayer");
                            // Check if the entity is a FakePlayer
                            entities.getAll().forEach(entity -> {
                                if (entity instanceof FakePlayerEntity) {
                                    fakePlayerName.set(entity.getName().getString());
                                    entity.remove(Entity.RemovalReason.DISCARDED);
                                }
                            });


                            context.getSource().sendSuccess(() -> Component.literal("§e" + fakePlayerName + " left the game"), false);

                            return Command.SINGLE_SUCCESS;
                        })
        );

    }
}