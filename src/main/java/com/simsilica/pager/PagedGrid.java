/*
 * $Id: PagedGrid.java 191 2014-07-24 08:48:22Z pspeed42 $
 * 
 * Copyright (c) 2014, Simsilica, LLC
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions 
 * are met:
 * 
 * 1. Redistributions of source code must retain the above copyright 
 *    notice, this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright 
 *    notice, this list of conditions and the following disclaimer in 
 *    the documentation and/or other materials provided with the 
 *    distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its 
 *    contributors may be used to endorse or promote products derived 
 *    from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS 
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.simsilica.pager;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.util.SafeArrayList;
import com.simsilica.builder.Builder;
import com.simsilica.builder.BuilderReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *  Manages a set of Zones for a particular grid model.  Zones
 *  are created and released as needed to keep a certain radius
 *  around the central location.  Parent/child zone relationships
 *  are managed such that child zones are not queued for building
 *  until the parent zone is available.
 *
 *  @author    Paul Speed
 */
public class PagedGrid {

    static Logger log = LoggerFactory.getLogger(PagedGrid.class);
    
    private Builder builder;
    private Node gridRoot;
    private ZoneFactory zoneFactory;    
    private float xCornerWorld;
    private float zCornerWorld;
    private int xCenterCell = Integer.MIN_VALUE;
    private int zCenterCell = Integer.MIN_VALUE;
    private float xWorld;
    private float zWorld;
    private Grid grid;
    private int radius;
    private int priorityBias = 1;
 
    private boolean trackViewLocation;
 
    private ZoneProxy[][][] cells;
    private int size;
    private int layers;

    private PagedGrid parent;
    private SafeArrayList<PagedGrid> children;
    
    // For double checking that we aren't leaking releases.
    private ConcurrentHashMap<Zone, ZoneProxy> releaseWatchDog = new ConcurrentHashMap<Zone, ZoneProxy>(); 

    private int appliedZoneCount = 0;
 
    /**
     *  Creates a root level paging system that will use the specified
     *  factory to create zones, the specified builder to build zones,
     *  and the grid and other settings to manage zone interest.
     */   
    public PagedGrid( ZoneFactory zoneFactory, Builder builder, 
                      Grid grid, int layers, int radius )
    {
        this(null, zoneFactory, builder, grid, layers, radius);
    }
    
    /**
     *  Creates a child paging system that will use the specified
     *  factory to create zones, the specified builder to build zones,
     *  and the grid and other settings to manage zone interest.
     *  Zones created in this paging system will be dependent on the
     *  zones of the parent system such that children won't be built
     *  until parents have been fully built.
     */   
    public PagedGrid( PagedGrid parent, ZoneFactory zoneFactory, Builder builder, 
                      Grid grid, int layers, int radius )
    {
        if( grid.getCellSize().x != grid.getCellSize().z ) {
            throw new IllegalArgumentException("Paged grids must be square in the x, z plane.");
        }
        this.parent = parent;
        this.zoneFactory = zoneFactory;
        this.builder = builder;        
        this.gridRoot = new Node("GridRoot");
        this.grid = grid;
        this.radius = radius;
        this.size = 2 * radius + 1;
        this.cells = new ZoneProxy[size][layers][size];
        this.layers = layers;
        
        if( this.parent != null ) {
            parent.addChild(this);
        }
    }
    
    public int getLayerCount() {
        return layers;
    }
    
    public int getRadius() {
        return radius;
    }
    
    public int getAppliedZoneCount() {
        return appliedZoneCount;
    }
    
    public int getMaxZoneCount() {
        return size * size * layers;
    }
 
    public void release() {
        // Force release all child paged grids...
        if( children != null ) {
            for( PagedGrid child : children ) {
                child.release();
            }
        }
 
        for( int i = 0; i < size; i++ ) {
            for( int j = 0; j < layers; j++ ) {
                for( int k = 0; k < size; k++ ) {
                    if( cells[i][j][k] != null ) {
                        // Force a release as we've already forced the
                        // children to release
                        if( log.isTraceEnabled() ) {
                            log.trace("release() release:" + cells[i][j][k] );
                        }                         
                        if( cells[i][j][k].isBuiltOnce()) {
                            builder.release(cells[i][j][k]);
                        }
                        cells[i][j][k] = null;
                    }
                }
            }
        }            
 
        // Set it to build after everything else... but note that
        // a shutdown is probably coming soon so if there are still
        // a lot of pending processes then this won't run.  It's a 
        // useful bit of double checking if everything is idle at
        // shutdown, though.       
        builder.build(new BuilderReference() {

                @Override
                public int getPriority() {
                    return Integer.MAX_VALUE;
                }
    
                @Override
                public void build() {
                }
    
                @Override
                public void apply( Builder builder ) {
                    if( !log.isDebugEnabled() ) {
                        return;
                    }
                    log.debug(PagedGrid.this + " unreleased zones:" + releaseWatchDog.keySet());
                    for( ZoneProxy proxy : releaseWatchDog.values() ) {
                        log.debug( proxy + "  unreleased children:" + proxy.children ); 
                    } 
                }

                @Override
                public void release( Builder builder ) {
                }
            });
    }
 
    public Grid getGrid() {
        return grid;
    }
 
    public Node getGridRoot() {
        return gridRoot;
    }
 
    public void setPriorityBias( int bias ) {
        this.priorityBias = bias;
    }
    
    public int getPriorityBias() {
        return priorityBias;
    }
    
    public void setTrackViewLocation( boolean b ) {
        this.trackViewLocation = b;
    }
    
    public boolean getTrackViewLocation() {
        return trackViewLocation;
    }
 
    protected void addChild( PagedGrid child ) {
        if( children == null ) {
            children = new SafeArrayList<PagedGrid>(PagedGrid.class);
        }
        children.add(child);
        
        if( xCenterCell != Integer.MIN_VALUE && zCenterCell != Integer.MIN_VALUE ) {
            // Then this grid has had its position set before and so we
            // should update the child
            child.setCenterWorldLocation(xWorld, zWorld);
        }
    }
 
    public Vector2f getCenterWorldLocation() {
        return new Vector2f(xWorld, zWorld);
    }
    
    public void setCenterWorldLocation( float x, float z ) {        
        if( setCenterCell(grid.toCellX(x), grid.toCellZ(z)) ) {
            recalculateCorner();            
        }        
 
        this.xWorld = x;
        this.zWorld = z;
        
        gridRoot.setLocalTranslation(-(x - xCornerWorld), 0, -(z - zCornerWorld));
 
        // Let the center cells know that the position has moved
        if( trackViewLocation ) {
            //long start = System.nanoTime();
            for( int i = radius - 1; i <= radius + 1; i++ ) {
                for( int j = radius - 1; j <= radius + 1; j++ ) {                    
                    for( int layer = 0; layer < layers; layer++ ) {
                        cells[i][layer][j].zone.setViewLocation(x, z);
                    }
                }
            }
            //long end = System.nanoTime();
            //System.out.println( "Updated view location in:" + ((end - start)/1000000.0) + " ms" );
        }            
                        
        if( children != null ) {
            for( PagedGrid child : children ) {
                child.setCenterWorldLocation(x, z);
            }
        }
    }
 
    protected void recalculateCorner() {
        xCornerWorld = grid.toWorldX(xCenterCell - radius);
        zCornerWorld = grid.toWorldZ(zCenterCell - radius);        
    }
 
    protected ZoneProxy getWorldCell( int xCellWorld, int yCellWorld, int zCellWorld ) { 
        int x = xCellWorld - (xCenterCell - radius);
        int z = zCellWorld - (zCenterCell - radius);
        if( x < 0 || z < 0 )
            return null;
        if( x >= size || z >= size )
            return null;
        return cells[x][yCellWorld][z];           
    }
 
    protected ZoneProxy removeWorldCell( int xCellWorld, int yCellWorld, int zCellWorld ) {
        int x = xCellWorld - (xCenterCell - radius);
        int z = zCellWorld - (zCenterCell - radius);
        if( x < 0 || z < 0 )
            return null;
        if( x >= size || z >= size )
            return null;
        
        ZoneProxy result = cells[x][yCellWorld][z];
        cells[x][yCellWorld][z] = null;             
        return result;
    }
    
    protected boolean setCenterCell( int xNew, int zNew ) {
        if( xCenterCell == xNew && zCenterCell == zNew ) {
            return false;
        }
 
        builder.pause();
 
        int xzSize = (int)grid.getCellSize().x; 
        int cellHeight = (int)grid.getCellSize().y;
               
        // Refresh the grid and offsets
        // Copy the ones from the old array to the new...
        // removing from the old as we go.  We'll clean up
        // the ones we don't use after.
        ZoneProxy[][][] newCells = new ZoneProxy[size][layers][size];
        Vector3f temp = new Vector3f();
        for( int x = -radius; x <= radius; x++ ) {
            for( int z = -radius; z <= radius; z++ ) {
                for( int y = 0; y < layers; y++ ) {
                    
                    // Remove it from the old array if it exists
                    ZoneProxy ref = removeWorldCell(xNew + x, y, zNew + z);
                    if( ref == null ) {
                        // Need to create one
                        ref = new ZoneProxy(zoneFactory.createZone(this, xNew + x, y, zNew + z));
                        
                        // Tell the zone its relative location before we build it
                        ref.zone.setRelativeGridLocation(x, y, z);
                        
                        if( parent == null ) {
                            builder.build(ref);
                        } else {
                            // We need to depend on parent zone(s).
                            // The zone won't get built until the parent is built
                            parent.addDependency(ref, grid); 
                        }                         
                    } 

                    newCells[x + radius][y][z + radius] = ref;
                    Zone zone = ref.zone;
                    Vector3f pos = grid.toWorld(x + radius, y, z + radius, temp);
                    zone.getZoneRoot().setLocalTranslation(pos);
                    zone.resetPriority(xNew, 0, zNew, priorityBias);
                    
                    // Tell this zone what it's current center-relative location
                    // is.  Rebuild it if necessary
                    if( zone.setRelativeGridLocation(x, y, z) ) {
                        if( parent == null ) {
                            // Just rebuild it
                            builder.build(ref);
                        } else {
                            // Let the parent decide when it needs
                            // to be rebuilt
                            parent.rebuildChild(ref);
                        }
                    }
                }
            }
        }
        
        // Remove any dead ones
        for( int i = 0; i < size; i++ ) {
            for( int j = 0; j < layers; j++ ) {
                for( int k = 0; k < size; k++ ) {
                    if( cells[i][j][k] != null ) {
                        // Let the zone decide when it gets released.
                        // It may have children, etc.
                        cells[i][j][k].markForRelease();
                    }
                }
            }
        }
 
        xCenterCell = xNew;
        zCenterCell = zNew;
 
        cells = newCells;        
        builder.resume();
        
        return true; 
    }
 
    protected void addDependency( ZoneProxy childZone, Grid childGrid ) {
        // Should really use a bounding box but for now we'll assume
        // one parent hits.
        float xWorld = childGrid.toWorldX(childZone.zone.getXCell());
        float yWorld = childGrid.toWorldY(childZone.zone.getYCell());
        float zWorld = childGrid.toWorldZ(childZone.zone.getZCell());
        
        int xCell = grid.toCellX(xWorld);
        int yCell = grid.toCellY(yWorld);
        int zCell = grid.toCellZ(zWorld);
 
        ZoneProxy parentZone = getWorldCell(xCell, yCell, zCell); 
        if( parentZone == null ) {
            log.warn("Parent zone is null for:" + xWorld + ", " + yWorld + ", " + zWorld);
            return;
        }
        
        childZone.addParent(parentZone);
        parentZone.addChild(childZone);
    }
 
    protected void rebuildChild( ZoneProxy childZone ) {
        // Right now we only support one parent per child
        // so this is easy
        ZoneProxy parentZone = childZone.parents.get(0);
        parentZone.rebuildChild(childZone);        
    }
    
    protected class ZoneProxy implements BuilderReference {
        private Zone zone;
        
        // Some of this class was written to support multiple
        // parents but practically, only one parent is allowed
        // right now.  Comments below spell out why but I may
        // someday want to support multiple parents so I feel
        // uncomfortable ripping it out completely.
        private SafeArrayList<ZoneProxy> parents;  // dependencies
        private SafeArrayList<ZoneProxy> children; // dependents
        private boolean applied = false;
        private boolean releasing = false;
        private boolean released = false;

        // Set to true if the zone has been built at least once.
        private AtomicBoolean builtOnce = new AtomicBoolean(false);
        
        public ZoneProxy( Zone zone ) {
            this.zone = zone;
        }
        
        public boolean isBuiltOnce() {
            return builtOnce.get();
        }

        public final void attach() {
            gridRoot.attachChild(zone.getZoneRoot());
        }

        public final void detach() {
            zone.getZoneRoot().removeFromParent();
        }

        @Override
        public final int getPriority() {
            return zone.getPriority();
        }

        @Override
        public final void build() {
            builtOnce.set(true);
            releaseWatchDog.put(zone, this);
            if( log.isTraceEnabled() ) {
                log.trace("Calling build() on:" + zone);
            }
            zone.build();
        }

        @Override
        public final void apply( Builder builder ) {
            applied = true;
            appliedZoneCount++;
            zone.apply(builder);
            
            // Since we only attach on apply() we can get away
            // with detaching on release().  release() is only
            // called if the zone is actually built.... and so
            // is apply().
            attach();
            
            // Let the children know the this parent has been built
            if( children != null ) {
                for( ZoneProxy child : children.getArray() ) {
                    child.parentApplied(this);
                }
            }
        }

        @Override
        public final void release( Builder builder ) {
            if( released ) {
                throw new RuntimeException("Zone already released:" + zone);
            }
            released = true;
            appliedZoneCount--;
            if( releaseWatchDog.remove(zone) == null ) {
                throw new RuntimeException("Watchdog missed a build()");
            }
            
            if( !builtOnce.get() ) {
                if( log.isTraceEnabled() ) {
                    log.trace("releasing unbuilt zone:" + zone + "  parents:" + parents );
                }            
                // In the case of children, they don't get passed to the builder
                // until the parents are built.  It is then possible that we might
                // pass this reference on to the builder for release without ever
                // having requested that it get built.  Normally the builder keeps 
                // track for us but in this case we are bypassing that check by 
                // delaying build().
                // But we only care if it's been built at least one time... then
                // we always need to release.
            } else {
                if( log.isTraceEnabled() ) {
                    log.trace("Calling release() on:" + zone);
                }
                zone.release(builder);
                detach();
            }
            
            // Even if we didn't actually release the zone, we still need to let the
            // parents know that this zone is clear... or they will never release.
            
            // Now we can let the parents know we are done
            dispose();
        }
 
        /**
         *  Removes this from any parent zones, etc. regardless of build
         *  or release state.  This is called as the last thing whenever a 
         *  zone is completely finished... even if it was never built.
         */ 
        protected void dispose() {
        
            if( log.isTraceEnabled() ) {
                log.trace("dispose():" + zone);
            }
                        
            // The need to do this so finally is partially due to how
            // parents keep track of children.  If they tracked built children
            // in addition to just "children" then the parent could be smart
            // enough to release itself without the children explicitly removing
            // themselves.
            // At any rate, having to call dispose from more than one place is a
            // sign that our state management could be simplified somewhere.
        
            if( parents != null ) {
                if( log.isTraceEnabled() ) {
                    log.trace("Removing from parents:" + parents + ", child:" + zone);
                }
                for( ZoneProxy parent : parents.getArray() ) {
                    parent.removeChild(this);   
                }
                parents = null;
            }
        }
        
        /**
         *  Called when the paged grid is done with this zone.
         *  If the zone doesn't have any children then it is simply
         *  passed to the builder for release.  If it does have
         *  children then they are notified.  Note: a properly 
         *  constructed hierarchy really shouldn't still have children
         *  by the time the parent is ready for release... but we
         *  check anyway.
         */
        public final void markForRelease() {
            if( log.isTraceEnabled() ) {
                log.trace("markForRelease():" + zone + "  parents:" + parents);
            }
            if( releasing ) {
                if( log.isTraceEnabled() ) {
                    log.trace("markForRelease() already releasing:" + zone);
                }            
                // We are already marked for release.  This can happen
                // when the parent has gone out of scope at the same time
                // this zone does.  The grid will mark this zone for release
                // and so will the parent.  But we should only release once.
                return;
            }
            releasing = true;
                        
            // Regardless of what we do, make the node invisible
            zone.getZoneRoot().setCullHint(CullHint.Always);             
            
            if( children != null ) {
                // We can't release yet... but we'll let the children know
                for( ZoneProxy child : children.getArray() ) {
                    child.markForRelease();
                }
            } else {
                // It's possible that we have not been submitted yet
                if( builder.isManaged(this) ) {
                    if( log.isTraceEnabled() ) {
                        log.trace("releasing:" + zone);
                    }
                    // If we are managed, then we _always_ need to release
                    // or the reference will be 'leaked'.            
                    builder.release(this);
                    
                    // But if we've never been built then we will not
                    // be called back... so we must dispose manually
                    if( !builtOnce.get() ) {
                        if( log.isTraceEnabled() ) {
                            log.trace("disposing because never built:" + zone);
                        }
                        dispose();            
                    }
                    
                } else {
                    // Need to let our parents know to forget us because we 
                    // will never be released
                    dispose();                    
                }
            }
        }
 
        protected void parentApplied( ZoneProxy parent ) {
            // Let the zone know about its parent depencies
            // (Note: we don't even get into this method unless we
            //  already know we have parents.)
            zone.setParentZone(parent.zone);
            
            // And right here we force one parent zone at a time.
            // It becomes really difficult for a child to deal
            // with multiple parents.  For one thing, they have
            // no way of properly translating the separated mesh
            // data because that's all local to the respective zones.
            // So if we ever do want to support multiple parent zones
            // then it needs to be explicitly spelled out... ie:
            // probably we go back to something like MeshSource except
            // it is an interface that provides the parent zones.
                
            // Right now we are only supporting one parent so we 
            // will short cut and assume it is ok for us to build
            builder.build(this);
        }
        
        protected void rebuild() {
            if( log.isTraceEnabled() ) {
                log.trace("rebuild():" + zone);
            }
            
            // We could keep track of additional state here
            // because "applied" is not the whole story in the
            // case of a rebuild.  
            applied = false;
            appliedZoneCount--;
            builder.build(this);
        }
 
        protected void addParent( ZoneProxy parent ) {
            if( parents == null ) {
                parents = new SafeArrayList<ZoneProxy>(ZoneProxy.class);                
            }
            parents.add(parent);
        }
 
        protected void addChild( ZoneProxy child ) {
            if( children == null ) {
                children = new SafeArrayList<ZoneProxy>(ZoneProxy.class);                
            }
            children.add(child);
            
            // If we are already built then go ahead and let the
            // child know
            if( applied ) {
                child.parentApplied(this);
            }
        }
    
        protected void rebuildChild( ZoneProxy child ) {
         
            // If we are already built then go ahead and let
            // the child build
            if( applied ) {
                child.rebuild();
            }   
        }
                
        protected void removeChild( ZoneProxy child ) {
            if( children == null ) {
                return;
            }
            
            children.remove(child);
            if( children.isEmpty() ) {
                children = null;
                
                if( releasing ) {
                    if( builder.isManaged(this) ) {
                        // Now we can really release
                        builder.release(this);
                    } else {
                        dispose();
                    }
                }
            }
        }
 
        @Override       
        public String toString() {
            return super.toString() + "[" + zone + "]";
        }
    }
}
