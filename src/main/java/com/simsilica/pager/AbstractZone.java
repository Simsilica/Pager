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


/**
 *
 *  @author    Paul Speed
 */
public abstract class AbstractZone implements Zone {

    private int xCell;
    private int yCell;
    private int zCell;
    private Node zoneRoot;
    private int priority;
    private Grid grid;
    private Zone parentZone;
    
    protected AbstractZone( Grid grid, int xCell, int yCell, int zCell ) {
        this.grid = grid;
        this.xCell = xCell;
        this.yCell = yCell;
        this.zCell = zCell;
        this.zoneRoot = new Node(getClass().getSimpleName() + "[" + xCell + ", " + yCell + ", " + zCell + "]");
    }

    @Override
    public Grid getGrid() {
        return grid;
    }

    @Override
    public int getXCell() {
        return xCell;
    }

    @Override
    public int getYCell() {
        return yCell;
    }

    @Override
    public int getZCell() {
        return zCell;
    }

    @Override
    public float getXWorld() {
        return grid.toWorldX(xCell);
    }

    @Override
    public float getYWorld() {
        return grid.toWorldY(yCell);
    }

    @Override
    public float getZWorld() {
        return grid.toWorldZ(zCell);
    }

    @Override
    public Vector3f getWorldLocation( Vector3f target ) {
        return grid.toWorld(xCell, yCell, zCell, target);
    }

    @Override
    public Node getZoneRoot() {
        return zoneRoot;
    }

    @Override
    public void resetPriority( int xCenter, int yCenter, int zCenter, int bias ) {
        int dx = xCell - xCenter;
        int dz = zCell - zCenter;
        this.priority = bias * (int)Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setParentZone( Zone parentZone ) {
        this.parentZone = parentZone;
    }

    public Zone getParentZone() {
        return parentZone;
    }
 
    /**
     *  Default implementation always returns false.
     */
    @Override   
    public boolean setRelativeGridLocation( int x, int y, int z ) {
        return false;
    }
    
    @Override
    public String toString() {
        return super.toString() + "[" + xCell + ", " + yCell + ", " + zCell + "]";
    }
}


