/*
 * $Id$
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

import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.util.SafeArrayList;
import com.simsilica.builder.Builder;
import com.simsilica.builder.BuilderReference;
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
    private Grid grid;
    private int radius;
    private int priorityBias = 1;
 
    private ZoneProxy[][][] cells;
    private int size;
    private int layers;

    private PagedGrid parent;
    private SafeArrayList<PagedGrid> children;
 
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
 
    protected void addChild( PagedGrid child ) {
        if( children == null ) {
            children = new SafeArrayList<PagedGrid>(PagedGrid.class);
        }
        children.add(child);
    }
    
    public void setCenterWorldLocation( float x, float z ) {
        if( setCenterCell(grid.toCellX(x), grid.toCellZ(z)) ) {
            recalculateCorner();            
        }        
 
        gridRoot.setLocalTranslation(-(x - xCornerWorld), 0, -(z - zCornerWorld));
                        
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
        
        public ZoneProxy( Zone zone ) {
            this.zone = zone;
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
            zone.build();
        }

        @Override
        public final void apply() {
            applied = true;
            zone.apply();
            
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
        public final void release() {
            if( !applied ) {
                // In the case of children, they don't get passed to the builder
                // until the parents are built.  It is then possible that we might
                // pass this reference on to the builder for release without ever
                // having applied it.  Normally the builder keeps track for us but
                // in this case we are bypassing that check by delaying build().
                return;
            }
            zone.release();
            detach();
            
            // Now we can let the parents know we are done
            if( parents != null ) {
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
            releasing = true;
            
            // Regardless of what we do, make the node invisible
            zone.getZoneRoot().setCullHint(CullHint.Always);
            
            if( children != null ) {
                // We can't release yet... but we'll let the children know
                for( ZoneProxy child : children.getArray() ) {
                    child.markForRelease();
                }
            } else {
                builder.release(this);
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
        
        protected void removeChild( ZoneProxy child ) {
            if( children == null ) {
                return;
            }
            
            children.remove(child);
            if( children.isEmpty() ) {
                children = null;
                
                if( releasing ) {
                    // Now we can really release
                    builder.release(this);
                }
            }
        }
    }
}
