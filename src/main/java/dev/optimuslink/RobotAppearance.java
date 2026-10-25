package dev.optimuslink;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.optimuslink.core.FleetRegistry;

/** Palette variants retain the original rig, scale, animations, hitbox and camera. */
final class RobotAppearance {
    private RobotAppearance() {}
    static String modelId(String colour) { return "white".equals(FleetRegistry.validColour(colour))?"OP_Optimus":"OP_Optimus_"+colour; }
    static String portrait(String colour) { return "Pages/OptimusLink/Portraits/"+FleetRegistry.validColour(colour)+"-bust.png"; }
    static String preview(String colour) { return "Pages/OptimusLink/Portraits/"+FleetRegistry.validColour(colour)+".png"; }
    static Model model(String colour) {
        var asset=ModelAsset.getAssetMap().getAsset(modelId(colour));
        if(asset==null)throw new IllegalStateException("Robot appearance is unavailable. No changes were saved.");
        return Model.createUnitScaleModel(asset);
    }
    static void apply(Ref<EntityStore> ref,Model model) {
        ref.getStore().putComponent(ref,ModelComponent.getComponentType(),new ModelComponent(model));
        ref.getStore().putComponent(ref,PersistentModel.getComponentType(),new PersistentModel(model.toReference()));
    }
}
