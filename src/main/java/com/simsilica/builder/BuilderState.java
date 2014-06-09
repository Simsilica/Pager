/*
 * ${Id}
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

package com.simsilica.arboreal;

import com.jme3.app.Application;
import com.simsilica.builder.Builder;
import com.simsilica.lemur.event.BaseAppState;


/**
 *
 *  @author    Paul Speed
 */
public class BuilderState extends BaseAppState {

    private Builder builder;
    private int maxUpdates; 
    
    public BuilderState( int poolSize, int maxUpdates ) {
        this.builder = new Builder("Builder", poolSize);
        this.maxUpdates = maxUpdates;
    }

    public Builder getBuilder() {
        return builder;
    }

    @Override
    protected void initialize( Application app ) {    
    }

    @Override
    protected void cleanup( Application app ) {
        builder.shutdown();
        
        // Can't restart it once shutdown so we might as well
        // poison the well
        builder = null;
    }

    @Override
    protected void enable() {
        // We have to check because the first time through
        // it won't be paused.
        if( builder.isPaused() ) {
            builder.resume();
        }
    }

    @Override
    public void update( float tpf ) {       
        builder.applyUpdates(maxUpdates);
    }

    @Override
    protected void disable() {
        builder.pause();
    }
}
