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

package com.simsilica.pager.debug;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.debug.WireBox;
import com.jme3.util.BufferUtils;
import com.simsilica.pager.AbstractZone;
import com.simsilica.pager.Grid;
import com.simsilica.pager.PagedGrid;
import com.simsilica.pager.Zone;
import com.simsilica.pager.ZoneFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 *  @author    Paul Speed
 */
public class BBoxZone extends AbstractZone {

    static Logger log = LoggerFactory.getLogger(BBoxZone.class);

    private Geometry boxGeom;
    private Material material;    

    public BBoxZone( Grid grid, Material material, int xCell, int yCell, int zCell ) {
        super(grid, xCell, yCell, zCell);
        this.material = material;
    }
    
    @Override
    public void build() {

        float scale = 1;
        if( getParentZone() != null ) {        
            log.info( "Building child... Parent mesh source:" + getParentZone() );
            scale = 0.99f;
        }    
        Vector3f size = getGrid().getCellSize();
        WireBox box = new WireBox(size.x * 0.5f * scale, size.y * 0.5f * scale, size.z * 0.5f * scale);
        boxGeom = new Geometry("box", box);
        boxGeom.setLocalTranslation(size.x * 0.5f, size.y * 0.5f, size.z * 0.5f);
        boxGeom.setMaterial(material);
    }

    @Override
    public void apply() {
        getZoneRoot().attachChild(boxGeom);
    }

    @Override
    public void release() {
        Mesh mesh = boxGeom.getMesh();
        for( VertexBuffer vb : mesh.getBufferList() ) {
            if( log.isTraceEnabled() ) {
                log.trace("--destroying buffer:" + vb);
            }
            BufferUtils.destroyDirectBuffer( vb.getData() );
        }        
    }
    
    public static class Factory implements ZoneFactory {
        private Material material;
        
        public Factory( Material material ) {
            this.material = material;
        }
        
        @Override
        public Zone createZone( PagedGrid pg, int xCell, int yCell, int zCell ) {
            Zone result = new BBoxZone(pg.getGrid(), material, xCell, yCell, zCell);
            return result;   
        }        
    }
}
