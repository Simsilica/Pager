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


/**
 *  Defines a 3D grid of some particular size and offset.
 *
 *  @author    Paul Speed
 */
public class Grid {

    private Vector3f offset;
    private Vector3f cellSize;
    
    public Grid( float xCellSize, float yCellSize, float zCellSize )
    {
        this(new Vector3f(xCellSize, yCellSize, zCellSize), new Vector3f());
    }
    
    public Grid( Vector3f cellSize, Vector3f offset )
    {
        this.cellSize = cellSize;
        this.offset = offset;
    }
 
    public Vector3f getCellSize()
    {
        return cellSize;
    }
    
    public Vector3f getOffset()
    {
        return offset;
    }
    
    public Vector3f toWorld( Vector3f cell )
    {
        return toWorld(cell, null);
    }
    
    public Vector3f toWorld( Vector3f cell, Vector3f target )    
    {
        return toWorld(cell.x, cell.y, cell.z, target);
    }

    public Vector3f toWorld( float xCell, float yCell, float zCell, Vector3f target )    
    {
        if( target == null ) {
            target = new Vector3f();
        }
        target.set(xCell * cellSize.x, yCell * cellSize.y, zCell * cellSize.z);
        target.addLocal(offset);
        return target;
    }
 
    public float toWorldX( int xCell ) {
        float x = (xCell * cellSize.x) + offset.x;
        return x;
    }

    public float toWorldY( int yCell ) {
        float y = (yCell * cellSize.y) + offset.y;
        return y;
    }

    public float toWorldZ( int zCell ) {
        float z = (zCell * cellSize.z) + offset.z;
        return z;
    }
    
    public Vector3f toCell( Vector3f world )
    {
        return toCell(world, null);
    }
    
    public Vector3f toCell( Vector3f world, Vector3f target )
    {
        if( target == null ) {
            target = new Vector3f();
        }
        target.set(world.x - offset.x, world.y - offset.y, world.z - offset.z);
        target.x = (float)Math.floor(target.x/cellSize.x);
        target.y = (float)Math.floor(target.y/cellSize.x);
        target.z = (float)Math.floor(target.z/cellSize.z);
        return target;
    }

    public int toCellX( float xWorld ) {
        float x = xWorld - offset.x;
        return (int)Math.floor(x/cellSize.x);
    } 

    public int toCellY( float yWorld ) {
        float y = yWorld - offset.y;
        return (int)Math.floor(y/cellSize.y);
    } 

    public int toCellZ( float zWorld ) {
        float z = zWorld - offset.z;
        return (int)Math.floor(z/cellSize.z);
    } 
    
    @Override
    public String toString() {
        return "Grid[cellSize:" + cellSize + ", offset:" + offset + "]";
    } 
}


