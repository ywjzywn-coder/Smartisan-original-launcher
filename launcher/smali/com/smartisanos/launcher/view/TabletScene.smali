.class public final Lcom/smartisanos/launcher/view/TabletScene;
.super Ljava/lang/Object;

# Read page model and geometry only on their owning GL thread.
.method public static publish(Lcom/smartisanos/launcher/view/b/fa;II)V
    .locals 8
    invoke-static {}, Lcom/smartisanos/launcher/data/TabletLayout;->supportsLandscapeSurface()Z
    move-result v0
    if-eqz v0, :end
    if-eqz p0, :hide
    const/16 v0, 0xc
    if-eq p2, v0, :single
    const/16 v0, 0x9
    if-ne p2, v0, :hide
    :single
    invoke-static {}, Lcom/smartisanos/launcher/view/Eb;->getInstance()Lcom/smartisanos/launcher/view/Eb;
    move-result-object v0
    invoke-virtual {v0}, Lcom/smartisanos/launcher/view/Eb;->isEditMode()Z
    move-result v0
    if-nez v0, :hide
    invoke-static {}, Lcom/smartisanos/launcher/ub;->getInstance()Lcom/smartisanos/launcher/ub;
    move-result-object v0
    const/4 v1, 0x1
    invoke-virtual {v0,v1}, Lcom/smartisanos/launcher/ub;->S(I)Z
    move-result v1
    if-nez v1, :hide
    const/4 v1, 0x2
    invoke-virtual {v0,v1}, Lcom/smartisanos/launcher/ub;->S(I)Z
    move-result v1
    if-nez v1, :hide
    const v1, 0x1000000
    invoke-virtual {v0,v1}, Lcom/smartisanos/launcher/ub;->S(I)Z
    move-result v1
    if-nez v1, :hide
    invoke-static {}, Lcom/smartisanos/launcher/data/TabletLayout;->navigationResizeContext()Landroid/content/Context;
    move-result-object v1
    if-eqz v1, :align_dock
    invoke-static {v1}, Lcom/smartisanos/launcher/data/Constants;->resizeTabletLayout(Landroid/content/Context;)V
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
    invoke-virtual {p0}, Lcom/smartisanos/launcher/view/b/fa;->Wq()Lcom/smartisanos/launcher/view/b/M;
    move-result-object v6
    if-eqz v6, :hide
    invoke-virtual {v6}, Lcom/smartisanos/launcher/view/b/M;->On()Z
    move-result v0
    if-nez v0, :hide
    iget v0, v6, Lcom/smartisanos/launcher/view/b/M;->pageIndex:I
    const/4 v1, 0x0
    if-nez p1, :geometry
    if-nez v0, :geometry
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
    iget v6,v6,Lcom/smartisanos/launcher/data/LayoutProperty;->page_view_margin_top:F
    sget p0,Lcom/smartisanos/launcher/data/Constants;->status_bar_height:I
    int-to-float p0,p0
    add-float/2addr v6,p0
    invoke-static/range {v0 .. v7}, Lcom/smartisanos/launcher/quickdesktop/TabletHome;->scene(IIIIFFFF)V
    goto :end
    :hide
    invoke-static {}, Lcom/smartisanos/launcher/quickdesktop/TabletHome;->hideScene()V
    :end
    return-void
.end method
