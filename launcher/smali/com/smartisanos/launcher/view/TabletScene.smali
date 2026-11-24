.class public final Lcom/smartisanos/launcher/view/TabletScene;
.super Ljava/lang/Object;

.field private static corners:[F

# Read page model and geometry only on their owning GL thread.
.method public static publish(Lcom/smartisanos/launcher/view/b/fa;II)V
    .locals 8
    invoke-static {}, Lcom/smartisanos/launcher/data/TabletLayout;->supportsLandscapeSurface()Z
    move-result v0
    if-eqz v0, :end
    if-eqz p0, :hide
    invoke-virtual {p0}, Lcom/smartisanos/launcher/view/b/fa;->getSinglePageMode()I
    move-result v0
    const/16 v1, 0xc
    if-eq v0, v1, :single
    const/16 v1, 0x9
    if-ne v0, v1, :hide
    :single
    invoke-static {}, Lcom/smartisanos/launcher/backup/TabletSlots;->takeMoves()Landroid/util/LongSparseArray;
    move-result-object v2
    if-eqz v2, :navigation
    invoke-static {}, Lcom/smartisanos/launcher/Aa;->qc()Ljava/util/HashMap;
    move-result-object v3
    const/4 v4, 0x0
    :sync_move
    invoke-virtual {v2}, Landroid/util/LongSparseArray;->size()I
    move-result v5
    if-ge v4,v5,:rebuild
    invoke-virtual {v2,v4}, Landroid/util/LongSparseArray;->keyAt(I)J
    move-result-wide v5
    invoke-static {v5,v6}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;
    move-result-object v5
    invoke-virtual {v3,v5}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v5
    check-cast v5,Lcom/smartisanos/launcher/data/ItemInfo;
    if-eqz v5,:next_move
    invoke-virtual {v2,v4}, Landroid/util/LongSparseArray;->valueAt(I)Ljava/lang/Object;
    move-result-object v6
    check-cast v6,[I
    const/4 v7,0x0
    aget v7,v6,v7
    iput v7,v5,Lcom/smartisanos/launcher/data/ItemInfo;->pageIndex:I
    const/4 v7,0x1
    aget v7,v6,v7
    iput v7,v5,Lcom/smartisanos/launcher/data/ItemInfo;->cellIndex:I
    :next_move
    add-int/lit8 v4,v4,1
    goto :sync_move
    :navigation
    invoke-static {}, Lcom/smartisanos/launcher/data/TabletLayout;->navigationResizeContext()Landroid/content/Context;
    move-result-object v1
    if-eqz v1, :align_dock
    invoke-static {v1}, Lcom/smartisanos/launcher/data/Constants;->resizeTabletLayout(Landroid/content/Context;)V
    :rebuild
    invoke-static {}, Lcom/smartisanos/smengine/Ra;->getInstance()Lcom/smartisanos/smengine/Ra;
    move-result-object v0
    invoke-virtual {v0}, Lcom/smartisanos/smengine/Ra;->jt()Lcom/smartisanos/smengine/Q;
    move-result-object v0
    invoke-virtual {v0}, Lcom/smartisanos/smengine/Q;->getRootNode()Lcom/smartisanos/smengine/SceneNode;
    move-result-object v0
    invoke-virtual {v0}, Lcom/smartisanos/smengine/SceneNode;->removeAllChildren()V
    invoke-static {}, Lcom/smartisanos/launcher/view/Eb;->getInstance()Lcom/smartisanos/launcher/view/Eb;
    move-result-object v0
    invoke-virtual {v0}, Lcom/smartisanos/launcher/view/Eb;->rebuildTabletPages()V
    goto :hide
    :align_dock
    invoke-static {}, Lcom/smartisanos/launcher/data/TabletLayout;->takeDockChange()Z
    move-result v0
    if-eqz v0, :read_page
    invoke-static {}, Lcom/smartisanos/launcher/view/Eb;->getInstance()Lcom/smartisanos/launcher/view/Eb;
    move-result-object v0
    invoke-virtual {v0}, Lcom/smartisanos/launcher/view/Eb;->zh()Lcom/smartisanos/launcher/view/V;
    move-result-object v0
    if-eqz v0, :read_page
    invoke-virtual {v0}, Lcom/smartisanos/launcher/view/V;->ap()V
    :read_page
    const/4 v0, 0x0
    invoke-virtual {p0,v0}, Lcom/smartisanos/launcher/view/b/fa;->Zb(I)Lcom/smartisanos/launcher/view/b/M;
    move-result-object v6
    if-eqz v6, :hide
    invoke-virtual {v6}, Lcom/smartisanos/launcher/view/b/M;->On()Z
    move-result v0
    if-nez v0, :hide
    iget v0, v6, Lcom/smartisanos/launcher/view/b/M;->pageIndex:I
    if-nez v0, :hide
    sget-object v1, Lcom/smartisanos/launcher/view/TabletScene;->corners:[F
    if-nez v1, :project
    const/16 v1, 0xc
    new-array v1, v1, [F
    sput-object v1, Lcom/smartisanos/launcher/view/TabletScene;->corners:[F
    :project
    invoke-static {}, Lcom/smartisanos/smengine/Ra;->getInstance()Lcom/smartisanos/smengine/Ra;
    move-result-object v2
    invoke-virtual {v2}, Lcom/smartisanos/smengine/Ra;->at()Lcom/smartisanos/smengine/k;
    move-result-object v2
    invoke-virtual {v2}, Lcom/smartisanos/smengine/k;->Vj()Lcom/smartisanos/smengine/Camera;
    move-result-object v2
    if-eqz v2, :hide
    invoke-virtual {v6,v2,v1}, Lcom/smartisanos/smengine/SceneNode;->getCornerPointScreenCoord(Lcom/smartisanos/smengine/Camera;[F)V
    invoke-virtual {p0}, Lcom/smartisanos/launcher/view/b/fa;->getSinglePageMode()I
    move-result v3
    if-ne v3,p2,:passive
    invoke-static {}, Lcom/smartisanos/launcher/view/Eb;->getInstance()Lcom/smartisanos/launcher/view/Eb;
    move-result-object v4
    invoke-virtual {v4}, Lcom/smartisanos/launcher/view/Eb;->isEditMode()Z
    move-result v4
    if-nez v4,:passive
    if-eqz p1,:projected
    :passive
    const/4 v0, 0x1
    :projected
    move p2,v3
    const/4 v1, 0x0
    const/4 v2, 0x0
    invoke-virtual {v6}, Lcom/smartisanos/smengine/SceneNode;->getChildCount()I
    move-result v5
    :loop
    if-ge v2,v5,:geometry
    invoke-virtual {v6,v2}, Lcom/smartisanos/smengine/SceneNode;->getChildAt(I)Lcom/smartisanos/smengine/SceneNode;
    move-result-object v3
    instance-of v4,v3,Lcom/smartisanos/launcher/view/a/g;
    if-eqz v4,:next
    check-cast v3,Lcom/smartisanos/launcher/view/a/g;
    invoke-virtual {v3}, Lcom/smartisanos/launcher/view/a/g;->Rl()Lcom/smartisanos/launcher/data/ItemInfo;
    move-result-object v3
    if-eqz v3,:next
    iget v4,v3,Lcom/smartisanos/launcher/data/ItemInfo;->cellIndex:I
    if-ltz v4,:next
    const/4 v3,0x1
    shl-int/2addr v3,v4
    or-int/2addr v1,v3
    :next
    add-int/lit8 v2,v2,0x1
    goto :loop
    :geometry
    invoke-static {p2}, Lcom/smartisanos/launcher/data/Constants;->mode(I)Lcom/smartisanos/launcher/data/LayoutProperty;
    move-result-object v6
    if-eqz v6,:hide
    iget v2,v6,Lcom/smartisanos/launcher/data/LayoutProperty;->page_cell_col_num:I
    iget v3,v6,Lcom/smartisanos/launcher/data/LayoutProperty;->page_cell_row_num:I
    iget v4,v6,Lcom/smartisanos/launcher/data/LayoutProperty;->cell_width:F
    iget v5,v6,Lcom/smartisanos/launcher/data/LayoutProperty;->cell_height:F
    iget v7,v6,Lcom/smartisanos/launcher/data/LayoutProperty;->dock_height:F
    sget-object v6, Lcom/smartisanos/launcher/view/TabletScene;->corners:[F
    invoke-static/range {v0 .. v7}, Lcom/smartisanos/launcher/quickdesktop/TabletHome;->scene(IIIIFF[FF)V
    goto :end
    :hide
    invoke-static {}, Lcom/smartisanos/launcher/quickdesktop/TabletHome;->hideScene()V
    :end
    return-void
.end method
