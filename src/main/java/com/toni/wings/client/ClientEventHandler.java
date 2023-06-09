package com.toni.wings.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import com.toni.wings.WingsMod;
import com.toni.wings.client.audio.WingsSound;
import com.toni.wings.client.flight.FlightView;
import com.toni.wings.client.flight.FlightViews;
import com.toni.wings.server.asm.AnimatePlayerModelEvent;
import com.toni.wings.server.asm.ApplyPlayerRotationsEvent;
import com.toni.wings.server.asm.EmptyOffHandPresentEvent;
import com.toni.wings.server.asm.GetCameraEyeHeightEvent;
import com.toni.wings.server.flight.Flights;
import com.toni.wings.util.MathH;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = WingsMod.ID)
public final class ClientEventHandler {
    private ClientEventHandler() {
    }

    @SubscribeEvent
    public static void onAnimatePlayerModel(AnimatePlayerModelEvent event) {
        Player player = event.getPlayer();
        Flights.get(player).ifPresent(flight -> {
            float delta = event.getTicksExisted() - player.tickCount;
            float amt = flight.getFlyingAmount(delta);
            if (amt == 0.0F) return;
            PlayerModel<?> model = event.getModel();
            float pitch = event.getPitch();
            model.head.xRot = MathH.toRadians(MathH.lerp(pitch, pitch / 4.0F - 90.0F, amt));
            model.leftArm.xRot = MathH.lerp(model.leftArm.xRot, -3.2F, amt);
            model.rightArm.xRot = MathH.lerp(model.rightArm.xRot, -3.2F, amt);
            model.leftLeg.xRot = MathH.lerp(model.leftLeg.xRot, 0.0F, amt);
            model.rightLeg.xRot = MathH.lerp(model.rightLeg.xRot, 0.0F, amt);
            model.hat.copyFrom(model.head);
        });
    }

    @SubscribeEvent
    public static void onApplyRotations(ApplyPlayerRotationsEvent event) {
        Flights.ifPlayer(event.getEntity(), (player, flight) -> {
            PoseStack matrixStack = event.getMatrixStack();
            float delta = event.getDelta();
            float amt = flight.getFlyingAmount(delta);
            if (amt > 0.0F) {
                float roll = MathH.lerpDegrees(
                    player.yBodyRotO - player.yRotO,
                    player.yBodyRot - player.getYRot(),
                    delta
                );
                float pitch = -MathH.lerpDegrees(player.xRotO, player.getXRot(), delta) - 90.0F;
                matrixStack.mulPose(Vector3f.ZP.rotationDegrees(MathH.lerpDegrees(0.0F, roll, amt)));
                matrixStack.mulPose(Vector3f.XP.rotationDegrees(MathH.lerpDegrees(0.0F, pitch, amt)));
                matrixStack.translate(0.0D, -1.2D * MathH.easeInOut(amt), 0.0D);
            }
        });
    }

    @SubscribeEvent
    public static void onGetCameraEyeHeight(GetCameraEyeHeightEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof LocalPlayer) {
            FlightViews.get((LocalPlayer) entity).ifPresent(flight ->
                flight.tickEyeHeight(event.getValue(), event::setValue)
            );
        }
    }

    @SubscribeEvent
    public static void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
        Flights.ifPlayer(Minecraft.getInstance().cameraEntity, (player, flight) -> {
            float delta = (float) event.getPartialTicks();
            float amt = flight.getFlyingAmount(delta);
            if (amt > 0.0F) {
                float roll = MathH.lerpDegrees(
                    player.yBodyRotO - player.yRotO,
                    player.yBodyRot - player.getYRot(),
                    delta
                );
                event.setRoll(MathH.lerpDegrees(0.0F, -roll * 0.25F, amt));
            }
        });
    }

    @SubscribeEvent
    public static void onEmptyOffHandPresentEvent(EmptyOffHandPresentEvent event) {
        Flights.get(event.getPlayer()).ifPresent(flight -> {
            if (flight.isFlying()) {
                event.setResult(Event.Result.ALLOW);
            }
        });
    }

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        Flights.ifPlayer(event.getEntity(), Player::isLocalPlayer, (player, flight) ->
            Minecraft.getInstance().getSoundManager().play(new WingsSound(player, flight))
        );
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player entity = event.player;
        if (event.phase == TickEvent.Phase.END && entity instanceof AbstractClientPlayer) {
            AbstractClientPlayer player = (AbstractClientPlayer) entity;
            FlightViews.get(player).ifPresent(FlightView::tick);
        }
    }
}
