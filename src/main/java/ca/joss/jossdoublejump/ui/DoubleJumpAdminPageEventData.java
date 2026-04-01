package ca.joss.jossdoublejump.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/** Payload for {@link DoubleJumpAdminPage} save events. */
public final class DoubleJumpAdminPageEventData {
    public static final BuilderCodec<DoubleJumpAdminPageEventData> CODEC =
        BuilderCodec.builder(DoubleJumpAdminPageEventData.class, DoubleJumpAdminPageEventData::new)
            .append(
                new KeyedCodec<>("@djHorizontalBoost", Codec.DOUBLE),
                (DoubleJumpAdminPageEventData d, Double v) -> d.djHorizontalBoost = v,
                d -> Double.valueOf(d.djHorizontalBoost))
            .add()
            .append(
                new KeyedCodec<>("@djVerticalBoost", Codec.DOUBLE),
                (DoubleJumpAdminPageEventData d, Double v) -> d.djVerticalBoost = v,
                d -> Double.valueOf(d.djVerticalBoost))
            .add()
            .append(
                new KeyedCodec<>("@djCooldownMs", Codec.DOUBLE),
                (DoubleJumpAdminPageEventData d, Double v) -> d.djCooldownMs = v,
                d -> Double.valueOf(d.djCooldownMs))
            .add()
            .append(
                new KeyedCodec<>("@djStaminaCost", Codec.DOUBLE),
                (DoubleJumpAdminPageEventData d, Double v) -> d.djStaminaCost = v,
                d -> Double.valueOf(d.djStaminaCost))
            .add()
            .append(
                new KeyedCodec<>("@djUsePercentageStamina", Codec.BOOLEAN),
                (DoubleJumpAdminPageEventData d, Boolean v) -> d.djUsePercentageStamina = v,
                d -> Boolean.valueOf(d.djUsePercentageStamina))
            .add()
            .append(
                new KeyedCodec<>("@djStaminaLossPercentage", Codec.DOUBLE),
                (DoubleJumpAdminPageEventData d, Double v) -> d.djStaminaLossPercentage = v,
                d -> Double.valueOf(d.djStaminaLossPercentage))
            .add()
            .append(
                new KeyedCodec<>("@infiniteDoubleJump", Codec.BOOLEAN),
                (DoubleJumpAdminPageEventData d, Boolean v) -> d.infiniteDoubleJump = v,
                d -> Boolean.valueOf(d.infiniteDoubleJump))
            .add()
            .append(
                new KeyedCodec<>("@djMaxJumps", Codec.DOUBLE),
                (DoubleJumpAdminPageEventData d, Double v) -> d.djMaxJumps = v,
                d -> Double.valueOf(d.djMaxJumps))
            .add()
            .build();

    public double djHorizontalBoost;
    public double djVerticalBoost;
    public double djCooldownMs;
    public double djStaminaCost;
    public boolean djUsePercentageStamina;
    public double djStaminaLossPercentage;
    public boolean infiniteDoubleJump;
    public double djMaxJumps;

    public DoubleJumpAdminPageEventData() {}
}
