package com.takem.titans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.takem.titans.TakemTitans;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.TitanAction;
import com.takem.titans.entity.titan.TitanAnimator;
import com.takem.titans.entity.titan.TitanSkeleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.util.FastColor.ARGB32;

/**
 * The titan body. Its boxes, their UVs and every titan skin come from one description in tools/titan_art.py: change the
 * body there and run it, never by hand below. Anatomy (chest, waist, shoulders, fingers, toes, teeth) hangs off the
 * fifteen joints of {@link TitanSkeleton}, so hit boxes and animation are unchanged; the {@code x_<style>} parts are
 * extras that only one kind of titan wears (the Armored's plates, the Beast's coat, the Jaw's bone mask...).
 */
public class TitanModel extends HierarchicalModel<AbstractTitan> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(TakemTitans.id("titan"), "main");
    public static final String[] STYLES = {"armored", "beast", "jaw", "cart", "spiked", "longhair", "messy"};
    private final ModelPart root;
    private final ModelPart[] joints = new ModelPart[15];
    private final ModelPart hair;
    private final ModelPart belly;
    private final ModelPart rightStump;
    private final ModelPart leftStump;
    private final ModelPart rightFingers;
    private final ModelPart leftFingers;
    private final List<ModelPart> extras = new ArrayList<>();
    private final List<String> extraStyles = new ArrayList<>();
    private final TitanSkeleton.Pose pose = new TitanSkeleton.Pose();
    private final TitanSkeleton.Frames frames = new TitanSkeleton.Frames();
    public int tint = -1;
    public boolean showBelly = true;
    /** Which extras to draw: a comma separated list of styles, or null for none. */
    public String style;

    public TitanModel(ModelPart root) {
        this.root = root;
        ModelPart base = root.getChild("root");
        this.joints[0] = base;
        this.joints[1] = base.getChild("hips");
        this.joints[2] = this.joints[1].getChild("torso");
        this.joints[3] = this.joints[2].getChild("head");
        this.joints[4] = this.joints[3].getChild("jaw");
        this.hair = this.joints[3].getChild("hair");
        this.belly = this.joints[2].getChild("belly");
        this.joints[5] = this.joints[2].getChild("right_arm");
        this.joints[6] = this.joints[5].getChild("right_forearm");
        this.joints[7] = this.joints[6].getChild("right_hand");
        this.joints[8] = this.joints[2].getChild("left_arm");
        this.joints[9] = this.joints[8].getChild("left_forearm");
        this.joints[10] = this.joints[9].getChild("left_hand");
        this.joints[11] = this.joints[1].getChild("right_leg");
        this.joints[12] = this.joints[11].getChild("right_shin");
        this.joints[13] = this.joints[1].getChild("left_leg");
        this.joints[14] = this.joints[13].getChild("left_shin");
        this.rightStump = this.joints[5].getChild("right_stump");
        this.leftStump = this.joints[8].getChild("left_stump");
        this.rightFingers = this.joints[7].getChild("right_fingers");
        this.leftFingers = this.joints[10].getChild("left_fingers");
        collectExtras(base, this.extras, this.extraStyles);
    }

    /** Every {@code x_<style>} part below {@code part}, with its style. */
    static void collectExtras(ModelPart part, List<ModelPart> parts, List<String> styles) {
        for (String style : STYLES) {
            if (part.hasChild("x_" + style)) {
                parts.add(part.getChild("x_" + style));
                styles.add(style);
            }
        }

        for (String child : CHILDREN) {
            if (part.hasChild(child)) {
                collectExtras(part.getChild(child), parts, styles);
            }
        }
    }

    private static final String[] CHILDREN = {
        "root", "hips", "pelvis", "torso", "belly", "head", "jaw", "hair", "right_arm", "right_stump", "right_forearm", "right_hand",
        "right_fingers", "left_arm", "left_stump", "left_forearm", "left_hand", "left_fingers", "right_leg", "right_shin", "left_leg",
        "left_shin"
    };

    /** Shows the extras of a skin's styles below {@code part} and hides the rest (gibs share their parts between skins). */
    public static void showStyle(ModelPart part, String style) {
        List<ModelPart> parts = new ArrayList<>();
        List<String> styles = new ArrayList<>();
        collectExtras(part, parts, styles);
        for (int i = 0; i < parts.size(); i++) {
            parts.get(i).visible = wears(style, styles.get(i));
        }
    }

    private static boolean wears(String styles, String style) {
        return styles != null && ("," + styles + ",").contains("," + style + ",");
    }

    /** The extras a titan skin wears, from its texture name ("pure_titan_5"). */
    public static String styleOf(String skin) {
        return SKIN_STYLES.get(skin);
    }

    // BEGIN GENERATED STYLES
    private static final Map<String, String> SKIN_STYLES = Map.ofEntries(
        Map.entry("pure_titan_1", "messy"),
        Map.entry("pure_titan_5", "longhair"),
        Map.entry("abnormal_titan_0", "messy"),
        Map.entry("abnormal_titan_2", "longhair"),
        Map.entry("crawler_titan_0", "longhair"),
        Map.entry("crawler_titan_1", "messy"),
        Map.entry("ghoul_titan_0", "longhair"),
        Map.entry("armored_titan_0", "armored"),
        Map.entry("armored_titan_1", "armored"),
        Map.entry("female_titan_0", "longhair"),
        Map.entry("female_titan_1", "longhair"),
        Map.entry("shifter_titan_0", "longhair"),
        Map.entry("war_hammer_titan_0", "longhair"),
        Map.entry("cart_titan_0", "cart,messy"),
        Map.entry("cart_titan_1", "cart,messy"),
        Map.entry("beast_titan_0", "beast,messy"),
        Map.entry("jaw_titan_0", "jaw,longhair"),
        Map.entry("ravager_titan_0", "spiked"),
        Map.entry("training_dummy_0", "messy")
    );
        // END GENERATED STYLES

    private static PartPose at(int joint) {
        return PartPose.offset(TitanSkeleton.PIVOT[joint][0], TitanSkeleton.PIVOT[joint][1], TitanSkeleton.PIVOT[joint][2]);
    }

    public static LayerDefinition createLayer() {
        // BEGIN GENERATED (tools/titan_art.py)
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition top = mesh.getRoot();
        PartDefinition root = top.addOrReplaceChild("root", CubeListBuilder.create(), at(0));
        PartDefinition hips = root.addOrReplaceChild("hips", CubeListBuilder.create(), at(1));
        PartDefinition pelvis = hips.addOrReplaceChild("pelvis", CubeListBuilder.create().texOffs(29, 107).addBox(-8.5F, -2F, -5F, 17F, 5F, 10F), PartPose.ZERO);
        PartDefinition torso = hips.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(173, 60).addBox(-8F, -11F, -5F, 16F, 11F, 10F).texOffs(81, 33).addBox(-10F, -25F, -6F, 20F, 14F, 12F).texOffs(155, 139).addBox(-9F, -24F, -7F, 18F, 7F, 1F).texOffs(0, 125).addBox(-11.5F, -27F, -5F, 23F, 3F, 10F).texOffs(129, 107).addBox(-4.5F, -32F, -2F, 9F, 6F, 9F), at(2));
        PartDefinition belly = torso.addOrReplaceChild("belly", CubeListBuilder.create().texOffs(67, 125).addBox(-8F, -12F, -8F, 16F, 10F, 3F), PartPose.ZERO);
        PartDefinition head = torso.addOrReplaceChild("head", CubeListBuilder.create().texOffs(77, 0).addBox(-9F, -18F, -9F, 18F, 12F, 18F).texOffs(234, 151).addBox(-1.5F, -11.8F, -11F, 3F, 4F, 2F).texOffs(56, 151).addBox(-10.5F, -13F, -1F, 2F, 5F, 3F).texOffs(70, 160).addBox(-6.5F, -6F, -9F, 13F, 1F, 1F).texOffs(56, 151).mirror().addBox(8.5F, -13F, -1F, 2F, 5F, 3F).mirror(false), at(3));
        PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(110, 60).addBox(-8F, 0F, -12F, 16F, 6F, 15F).texOffs(51, 160).addBox(-4F, 3.5F, -13F, 8F, 2F, 1F).texOffs(99, 160).addBox(-6.5F, -1F, -11.6F, 13F, 1F, 1F), at(4));
        PartDefinition hair = head.addOrReplaceChild("hair", CubeListBuilder.create().texOffs(150, 0).addBox(-9F, -18F, -9F, 18F, 12F, 18F, new CubeDeformation(0.6F)), PartPose.ZERO);
        PartDefinition rarm = torso.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(106, 125).addBox(-3.5F, -3F, -3.5F, 7F, 6F, 7F).texOffs(150, 85).addBox(-3F, 0F, -3F, 6F, 14F, 6F).texOffs(17, 151).addBox(-2.5F, 3F, -4F, 5F, 7F, 1F), at(5));
        PartDefinition rstump = rarm.addOrReplaceChild("right_stump", CubeListBuilder.create().texOffs(194, 139).addBox(-3F, 13F, -3F, 6F, 2F, 6F, new CubeDeformation(0.15F)), PartPose.ZERO);
        PartDefinition rforearm = rarm.addOrReplaceChild("right_forearm", CubeListBuilder.create().texOffs(45, 85).addBox(-3F, 0F, -3F, 6F, 15F, 6F).texOffs(168, 125).addBox(-3F, 1F, -3F, 6F, 6F, 6F, new CubeDeformation(0.4F)), at(6));
        PartDefinition rhand = rforearm.addOrReplaceChild("right_hand", CubeListBuilder.create().texOffs(132, 139).addBox(-3.5F, 0F, -2F, 7F, 5F, 4F).texOffs(196, 151).addBox(2F, 0.5F, -3.5F, 2F, 5F, 2F), at(7));
        PartDefinition rfingers = rhand.addOrReplaceChild("right_fingers", CubeListBuilder.create().texOffs(205, 151).addBox(-3.5F, 0F, -1.25F, 1F, 5F, 2F).texOffs(67, 151).addBox(-1.7F, 0F, -1.25F, 1F, 6F, 2F).texOffs(74, 151).addBox(0.1F, 0F, -1.25F, 1F, 6F, 2F).texOffs(212, 151).addBox(1.9F, 0F, -1.25F, 1F, 5F, 2F), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.35F, 0.0F, 0.0F));
        PartDefinition larm = torso.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(106, 125).mirror().addBox(-3.5F, -3F, -3.5F, 7F, 6F, 7F).mirror(false).texOffs(150, 85).mirror().addBox(-3F, 0F, -3F, 6F, 14F, 6F).mirror(false).texOffs(17, 151).mirror().addBox(-2.5F, 3F, -4F, 5F, 7F, 1F).mirror(false), at(8));
        PartDefinition lstump = larm.addOrReplaceChild("left_stump", CubeListBuilder.create().texOffs(194, 139).mirror().addBox(-3F, 13F, -3F, 6F, 2F, 6F, new CubeDeformation(0.15F)).mirror(false), PartPose.ZERO);
        PartDefinition lforearm = larm.addOrReplaceChild("left_forearm", CubeListBuilder.create().texOffs(45, 85).mirror().addBox(-3F, 0F, -3F, 6F, 15F, 6F).mirror(false).texOffs(168, 125).mirror().addBox(-3F, 1F, -3F, 6F, 6F, 6F, new CubeDeformation(0.4F)).mirror(false), at(9));
        PartDefinition lhand = lforearm.addOrReplaceChild("left_hand", CubeListBuilder.create().texOffs(132, 139).mirror().addBox(-3.5F, 0F, -2F, 7F, 5F, 4F).mirror(false).texOffs(196, 151).mirror().addBox(-4F, 0.5F, -3.5F, 2F, 5F, 2F).mirror(false), at(10));
        PartDefinition lfingers = lhand.addOrReplaceChild("left_fingers", CubeListBuilder.create().texOffs(205, 151).mirror().addBox(2.5F, 0F, -1.25F, 1F, 5F, 2F).mirror(false).texOffs(67, 151).mirror().addBox(0.7F, 0F, -1.25F, 1F, 6F, 2F).mirror(false).texOffs(74, 151).mirror().addBox(-1.1F, 0F, -1.25F, 1F, 6F, 2F).mirror(false).texOffs(212, 151).mirror().addBox(-2.9F, 0F, -1.25F, 1F, 5F, 2F).mirror(false), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.35F, 0.0F, 0.0F));
        PartDefinition rleg = hips.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(113, 85).addBox(-4.5F, -1F, -4.5F, 9F, 11F, 9F).texOffs(0, 151).addBox(-3.5F, 1F, -5.5F, 7F, 7F, 1F), at(11));
        PartDefinition rshin = rleg.addOrReplaceChild("right_shin", CubeListBuilder.create().texOffs(0, 107).addBox(-3.5F, 0F, -3.5F, 7F, 10F, 7F).texOffs(9, 160).addBox(-2.5F, -1.5F, -4.5F, 5F, 3F, 1F).texOffs(219, 151).addBox(-3F, 1F, 3.5F, 6F, 5F, 1F).texOffs(203, 107).addBox(-3.5F, 7F, -7.5F, 7F, 3F, 11F).texOffs(22, 160).addBox(1.4F, 8.4F, -9F, 2F, 2F, 2F).texOffs(128, 160).addBox(-0.4F, 8.8F, -8.6F, 1F, 1F, 1F).texOffs(133, 160).addBox(-1.9F, 8.9F, -8.5F, 1F, 1F, 1F).texOffs(138, 160).addBox(-3.3F, 9F, -8.4F, 1F, 1F, 1F), at(12));
        PartDefinition lleg = hips.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(113, 85).mirror().addBox(-4.5F, -1F, -4.5F, 9F, 11F, 9F).mirror(false).texOffs(0, 151).mirror().addBox(-3.5F, 1F, -5.5F, 7F, 7F, 1F).mirror(false), at(13));
        PartDefinition lshin = lleg.addOrReplaceChild("left_shin", CubeListBuilder.create().texOffs(0, 107).mirror().addBox(-3.5F, 0F, -3.5F, 7F, 10F, 7F).mirror(false).texOffs(9, 160).mirror().addBox(-2.5F, -1.5F, -4.5F, 5F, 3F, 1F).mirror(false).texOffs(219, 151).mirror().addBox(-3F, 1F, 3.5F, 6F, 5F, 1F).mirror(false).texOffs(203, 107).mirror().addBox(-3.5F, 7F, -7.5F, 7F, 3F, 11F).mirror(false).texOffs(22, 160).mirror().addBox(-3.4F, 8.4F, -9F, 2F, 2F, 2F).mirror(false).texOffs(128, 160).mirror().addBox(-0.6F, 8.8F, -8.6F, 1F, 1F, 1F).mirror(false).texOffs(133, 160).mirror().addBox(0.9F, 8.9F, -8.5F, 1F, 1F, 1F).mirror(false).texOffs(138, 160).mirror().addBox(2.3F, 9F, -8.4F, 1F, 1F, 1F).mirror(false), at(14));
        torso.addOrReplaceChild("x_armored", CubeListBuilder.create().texOffs(0, 139).addBox(-10.5F, -25.5F, -7.6F, 21F, 9F, 2F).texOffs(135, 125).addBox(-7F, -15.5F, -6.6F, 14F, 10F, 2F).texOffs(84, 107).addBox(-10F, -26F, 5.5F, 20F, 13F, 2F), PartPose.ZERO);
        head.addOrReplaceChild("x_armored", CubeListBuilder.create().texOffs(0, 60).addBox(-9.6F, -18.8F, -9.6F, 19F, 5F, 19F).texOffs(193, 125).addBox(-9.8F, -13F, -9.8F, 4F, 6F, 6F).texOffs(193, 125).mirror().addBox(5.8F, -13F, -9.8F, 4F, 6F, 6F).mirror(false), PartPose.ZERO);
        rarm.addOrReplaceChild("x_armored", CubeListBuilder.create().texOffs(166, 107).addBox(-4.5F, -4.2F, -4.5F, 9F, 6F, 9F), PartPose.ZERO);
        rforearm.addOrReplaceChild("x_armored", CubeListBuilder.create().texOffs(175, 85).addBox(-3.6F, 2F, -3.6F, 7F, 11F, 7F), PartPose.ZERO);
        larm.addOrReplaceChild("x_armored", CubeListBuilder.create().texOffs(166, 107).mirror().addBox(-4.5F, -4.2F, -4.5F, 9F, 6F, 9F).mirror(false), PartPose.ZERO);
        lforearm.addOrReplaceChild("x_armored", CubeListBuilder.create().texOffs(175, 85).mirror().addBox(-3.4F, 2F, -3.6F, 7F, 11F, 7F).mirror(false), PartPose.ZERO);
        rleg.addOrReplaceChild("x_armored", CubeListBuilder.create().texOffs(86, 139).addBox(-4.8F, 0F, -5.7F, 10F, 8F, 2F), PartPose.ZERO);
        rshin.addOrReplaceChild("x_armored", CubeListBuilder.create().texOffs(111, 139).addBox(-4F, -1F, -4.8F, 8F, 8F, 2F), PartPose.ZERO);
        lleg.addOrReplaceChild("x_armored", CubeListBuilder.create().texOffs(86, 139).mirror().addBox(-5.2F, 0F, -5.7F, 10F, 8F, 2F).mirror(false), PartPose.ZERO);
        lshin.addOrReplaceChild("x_armored", CubeListBuilder.create().texOffs(111, 139).mirror().addBox(-4F, -1F, -4.8F, 8F, 8F, 2F).mirror(false), PartPose.ZERO);
        torso.addOrReplaceChild("x_beast", CubeListBuilder.create().texOffs(0, 0).addBox(-11.5F, -28F, -7.5F, 23F, 17F, 15F), PartPose.ZERO);
        head.addOrReplaceChild("x_beast", CubeListBuilder.create().texOffs(146, 33).addBox(-10F, -19F, -1F, 20F, 14F, 11F), PartPose.ZERO);
        rarm.addOrReplaceChild("x_beast", CubeListBuilder.create().texOffs(77, 60).addBox(-4.2F, -3.5F, -4.2F, 8F, 16F, 8F), PartPose.ZERO);
        rforearm.addOrReplaceChild("x_beast", CubeListBuilder.create().texOffs(204, 85).addBox(-3.8F, 0F, -3.8F, 7F, 11F, 7F), PartPose.ZERO);
        larm.addOrReplaceChild("x_beast", CubeListBuilder.create().texOffs(77, 60).mirror().addBox(-3.8F, -3.5F, -4.2F, 8F, 16F, 8F).mirror(false), PartPose.ZERO);
        lforearm.addOrReplaceChild("x_beast", CubeListBuilder.create().texOffs(204, 85).mirror().addBox(-3.2F, 0F, -3.8F, 7F, 11F, 7F).mirror(false), PartPose.ZERO);
        head.addOrReplaceChild("x_jaw", CubeListBuilder.create().texOffs(47, 139).addBox(-8.6F, -17.6F, -10.4F, 17F, 8F, 2F), PartPose.ZERO);
        jaw.addOrReplaceChild("x_jaw", CubeListBuilder.create().texOffs(81, 151).addBox(-8.5F, 0.5F, -13.4F, 17F, 5F, 2F), PartPose.ZERO);
        rfingers.addOrReplaceChild("x_jaw", CubeListBuilder.create().texOffs(31, 160).addBox(-3.5F, 5F, -1F, 1F, 3F, 1F).texOffs(36, 160).addBox(-1.7F, 6F, -1F, 1F, 3F, 1F).texOffs(41, 160).addBox(0.1F, 6F, -1F, 1F, 3F, 1F).texOffs(46, 160).addBox(1.9F, 5F, -1F, 1F, 3F, 1F), PartPose.ZERO);
        lfingers.addOrReplaceChild("x_jaw", CubeListBuilder.create().texOffs(31, 160).mirror().addBox(2.5F, 5F, -1F, 1F, 3F, 1F).mirror(false).texOffs(36, 160).mirror().addBox(0.7F, 6F, -1F, 1F, 3F, 1F).mirror(false).texOffs(41, 160).mirror().addBox(-1.1F, 6F, -1F, 1F, 3F, 1F).mirror(false).texOffs(46, 160).mirror().addBox(-2.9F, 5F, -1F, 1F, 3F, 1F).mirror(false), PartPose.ZERO);
        torso.addOrReplaceChild("x_cart", CubeListBuilder.create().texOffs(0, 85).addBox(-8F, -25F, 6F, 16F, 15F, 6F), PartPose.ZERO);
        head.addOrReplaceChild("x_cart", CubeListBuilder.create().texOffs(0, 33).addBox(-9.8F, -19.2F, -9.8F, 20F, 6F, 20F), PartPose.ZERO);
        torso.addOrReplaceChild("x_spiked", CubeListBuilder.create().texOffs(30, 151).addBox(-1F, -25F, 6F, 2F, 4F, 4F).texOffs(43, 151).addBox(-1F, -19F, 6F, 2F, 4F, 4F).texOffs(185, 151).addBox(-1F, -13F, 6F, 2F, 4F, 3F).texOffs(245, 151).addBox(-1F, -7F, 5F, 2F, 3F, 3F), PartPose.ZERO);
        rarm.addOrReplaceChild("x_spiked", CubeListBuilder.create().texOffs(0, 160).addBox(-1F, -5F, -1F, 2F, 3F, 2F), PartPose.ZERO);
        larm.addOrReplaceChild("x_spiked", CubeListBuilder.create().texOffs(0, 160).mirror().addBox(-1F, -5F, -1F, 2F, 3F, 2F).mirror(false), PartPose.ZERO);
        head.addOrReplaceChild("x_longhair", CubeListBuilder.create().texOffs(70, 85).addBox(-8.5F, -17F, 6F, 17F, 16F, 4F), PartPose.ZERO);
        hair.addOrReplaceChild("x_messy", CubeListBuilder.create().texOffs(143, 151).addBox(-7F, -20.2F, -5F, 5F, 2F, 5F).texOffs(120, 151).addBox(0.5F, -20.6F, -7F, 6F, 2F, 5F).texOffs(219, 139).addBox(-4F, -20.4F, 1.5F, 6F, 2F, 6F).texOffs(164, 151).addBox(3.5F, -20F, 2F, 5F, 2F, 5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 168);
        // END GENERATED
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    public void prepareMobModel(AbstractTitan titan, float limbSwing, float limbSwingAmount, float partialTick) {
        TitanAnimator.pose(titan, partialTick, this.pose, this.frames);

        for (int i = 0; i < 15; i++) {
            ModelPart part = this.joints[i];
            part.x = TitanSkeleton.PIVOT[i][0];
            part.y = TitanSkeleton.PIVOT[i][1];
            part.z = TitanSkeleton.PIVOT[i][2];
            part.xRot = this.pose.xRot[i];
            part.yRot = this.pose.yRot[i];
            part.zRot = this.pose.zRot[i];
        }

        this.joints[0].x = this.joints[0].x + this.pose.rootX;
        this.joints[0].y = this.joints[0].y + this.pose.rootY;
        this.joints[0].z = this.joints[0].z + this.pose.rootZ;
        ModelPart head = this.joints[3];
        head.xScale = head.yScale = head.zScale = this.pose.headScale;
        // a slow breath swells the chest; it stops when the titan does
        float breath = titan.dyingTicks > 0 ? 0.0F : Mth.sin(((float)titan.tickCount + partialTick) * 0.06F + (float)titan.getId()) * 0.018F;
        ModelPart torso = this.joints[2];
        torso.xScale = torso.zScale = 1.0F + breath;
        torso.yScale = 1.0F + breath * 0.4F;
        boolean rightCut = titan.isArmCut(-1);
        boolean leftCut = titan.isArmCut(1);
        this.joints[6].visible = !rightCut;
        this.joints[9].visible = !leftCut;
        this.rightStump.visible = rightCut;
        this.leftStump.visible = leftCut;
        this.belly.visible = this.showBelly;
        this.joints[3].visible = !titan.isHeadless();
        this.joints[12].visible = !titan.isLegCut(-1);
        this.joints[14].visible = !titan.isLegCut(1);
        this.hair.visible = true;
        this.rightFingers.xRot = fingerCurl(titan, -1, partialTick);
        this.leftFingers.xRot = fingerCurl(titan, 1, partialTick);

        for (int i = 0; i < this.extras.size(); i++) {
            this.extras.get(i).visible = wears(this.style, this.extraStyles.get(i));
        }
    }

    /** Fingers hang half curled; a hand that grabs opens wide, then clenches round what it caught. */
    private static float fingerCurl(AbstractTitan titan, int side, float pt) {
        TitanAction a = titan.getAction();
        float relaxed = 0.35F;
        if (a == TitanAction.NONE || a.side() != (float)side && !a.isHold() && !a.isBite()) {
            return relaxed;
        }

        float k = Mth.clamp(((float)titan.actionTicks + pt) / (float)a.duration, 0.0F, 1.0F);
        if (a.isHold()) {
            return a.side() == (float)side ? 1.45F : relaxed;
        } else if (a == TitanAction.GRAB_LEFT || a == TitanAction.GRAB_RIGHT) {
            return k < 0.5F ? Mth.lerp(k / 0.5F, relaxed, -0.35F) : (k < 0.66F ? Mth.lerp((k - 0.5F) / 0.16F, -0.35F, 1.45F) : 1.45F);
        } else if (a == TitanAction.SWAT_LEFT || a == TitanAction.SWAT_RIGHT) {
            return -0.2F;
        } else {
            return relaxed;
        }
    }

    public void setupAnim(AbstractTitan titan, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        int c = color;
        if (this.tint != -1) {
            c = ARGB32.multiply(color, this.tint);
        }

        super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, c);
    }
}
