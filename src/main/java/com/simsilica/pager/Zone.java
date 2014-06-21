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
import com.simsilica.builder.BuilderReference;


/**
 *  Represents a Zone in particular grid witihin a paging
 *  system.  Zones are setup to be built in a Builder and
 *  managed in a Pager.  The specific 'thing' being built
 *  is up to the implementor.
 *
 *  @author    Paul Speed
 */
public interface Zone extends BuilderReference {
    
    public Node getZoneRoot();

    public Grid getGrid();

    /**
     *  When this zone is dependent on a parent zone then the
     *  parent zone will be set when the parent zone has
     *  been built and before this zone is queued for building. 
     */
    public void setParentZone( Zone parentZone );

    /**
     *  Called be the pager to set the current center-cell relative
     *  location of this zone.  The Zone can use this for LOD determination
     *  or other cell-relative stuff.  If the zone would need to be
     *  rebuilt as a result of this change then this method should
     *  return true.
     */
    public boolean setRelativeGridLocation( int x, int y, int z );

    public int getXCell();
    public int getYCell();
    public int getZCell();

    public float getXWorld();
    public float getYWorld();
    public float getZWorld();
    public Vector3f getWorldLocation( Vector3f target );
    
    public void resetPriority( int xCenter, int yCenter, int zCenter, int bias );
}
