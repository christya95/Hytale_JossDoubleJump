package ca.joss.jossdoublejump.mixin;

import ca.joss.jossdoublejump.edge.JumpEdgeIngress;
import ca.joss.jossdoublejump.edge.JumpEdgeMessage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ingests {@code jdj:v1:…} custom page payloads as jump-edge messages without forwarding them to UI page logic.
 */
@Mixin(PageManager.class)
public class PageManagerMixin {

    @Inject(
        method = "handleEvent",
        at = @At("HEAD"),
        cancellable = true
    )
    private void jdj$jumpEdgeIngress(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        CustomPageEvent event,
        CallbackInfo ci
    ) {
        if (event == null) {
            return;
        }
        String data = event.data;
        if (data == null || !data.startsWith(JumpEdgeMessage.PREFIX)) {
            return;
        }
        if (ref == null || !ref.isValid() || store == null) {
            return;
        }
        Player player = (Player) store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        PlayerRef playerRef = player.getPlayerRef();
        if (playerRef == null) {
            return;
        }
        JumpEdgeIngress.onWirePayload(playerRef, data);
        ci.cancel();
    }
}
