package ca.joss.jossdoublejump;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.Interaction;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.SimpleInteraction;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public class DoubleJumpInteraction extends SimpleInstantInteraction {

    private static final HytaleLogger LOGGER = DoubleJumpLogger.get();

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Nonnull
    public static final BuilderCodec<DoubleJumpInteraction> CODEC =
        ((BuilderCodec.Builder) BuilderCodec.builder(
                    DoubleJumpInteraction.class,
                    DoubleJumpInteraction::new,
                    (BuilderCodec) SimpleInstantInteraction.CODEC))
                .documentation("Double Jump — airborne boost toward look direction.")
                .build();

    public DoubleJumpInteraction() {}

    public DoubleJumpInteraction(String id) {
        super(id);
    }

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(
        @Nonnull InteractionType type,
        @Nonnull InteractionContext ctx,
        @Nonnull CooldownHandler cooldown
    ) {
        CommandBuffer<EntityStore> buf = ctx.getCommandBuffer();
        Ref<EntityStore> ref = ctx.getEntity();
        DoubleJumpConfig cfg = DoubleJumpConfig.get();

        if (buf == null) {
            fail(ctx, "no command buffer");
            return;
        }
        if (!ref.isValid()) {
            fail(ctx, "invalid entity ref");
            return;
        }
        if (cfg == null) {
            fail(ctx, "DoubleJumpConfig is null");
            return;
        }
        DoubleJumpComponent dj = (DoubleJumpComponent) buf.getComponent(ref, DoubleJump.getDoubleJumpComponentType());
        if (dj == null) {
            fail(ctx, "no DoubleJumpComponent");
            return;
        }
        boolean ok = DoubleJumpExecutor.tryApply(ref, buf, dj, cfg);
        ctx.getState().state = ok ? InteractionState.Finished : InteractionState.Failed;
    }

    private static void fail(InteractionContext ctx, String detail) {
        ((HytaleLogger.Api) LOGGER.atInfo()).log("FAILED: " + detail);
        ctx.getState().state = InteractionState.Failed;
    }

    @Nonnull
    @Override
    protected Interaction generatePacket() {
        return new SimpleInteraction();
    }

    @Override
    public boolean needsRemoteSync() {
        return needsRemoteSync((String) next) || needsRemoteSync((String) failed);
    }

    @Nonnull
    @Override
    public String toString() {
        return "DoubleJumpInteraction{} " + super.toString();
    }
}
