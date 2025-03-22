package org.furranystudio.thefakeplayer.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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

import java.util.List;

import static com.ibm.icu.lang.UCharacter.GraphemeClusterBreak.T;
import static com.mojang.text2speech.Narrator.LOGGER;

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

                            List<FakePlayerEntity> fakePlayers = world.getEntitiesOfClass(FakePlayerEntity.class, world.getWorldBorder().getCollisionShape().bounds());
                            if(!fakePlayers.isEmpty()) {
                                source.sendSuccess(() -> Component.literal("§cTheFakePlayer is already in the game"), false);
                                return Command.SINGLE_SUCCESS;
                            }

                            Player player = source.getPlayerOrException();

                            FakePlayerEntity fakePlayer = new FakePlayerEntity(world, player.getX(), player.getY(), player.getZ());

                            fakePlayer.setPos(player.getX(), player.getY(), player.getZ());

                            world.addFreshEntity(fakePlayer);

                            source.sendSuccess(() -> Component.literal("§eTheFakePlayer joined the game"), false);

                            return Command.SINGLE_SUCCESS;
                        })
        );

        dispatcher.register(
                Commands.literal("stopfakeplayer") // the command to stop the fake player
                        .executes(context -> {

                            CommandSourceStack source = context.getSource();
                            ServerLevel world = source.getLevel();

                            /*// Pick all entities NOT OPTIMIZED
                            LevelEntityGetter<Entity> entities = world.getEntities();
                            // Check if the entity is a FakePlayer
                            entities.getAll().forEach(entity -> {
                                if (entity instanceof FakePlayerEntity) {
                                    entity.remove(Entity.RemovalReason.DISCARDED);
                                }
                            });*/

                            // Pick all entities OPTIMIZED
                            List<FakePlayerEntity> fakePlayers = world.getEntitiesOfClass(FakePlayerEntity.class, world.getWorldBorder().getCollisionShape().bounds());
                            fakePlayers.forEach(Entity::discard);

                            context.getSource().sendSuccess(() -> Component.literal("§eTheFakePlayer left the game"), false);

                            return Command.SINGLE_SUCCESS;
                        })
        );

    }
}