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
 
package com.simsilica.arboreal.builder;



import java.util.ArrayList;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;

import org.slf4j.LoggerFactory;


/**
 *  A set of worker threads and management for building
 *  things in the background with proper hooks for
 *  resolving them on a specific thread (say the rendering
 *  thread, for example).
 *
 *  Here temporarily until it gets its own library...
 *
 *  @author    Paul Speed
 */
public class Builder {

    static Logger log = LoggerFactory.getLogger(Builder.class);

    private static AtomicLong instanceCount = new AtomicLong();

    private String name;

    private Map<BuilderReference,PrioritizedRef> refMap = new ConcurrentHashMap<BuilderReference,PrioritizedRef>();
    private PriorityBlockingQueue<Runnable> queue = new PriorityBlockingQueue<Runnable>();
    private ConcurrentLinkedQueue<PrioritizedRef> pausedItems = new ConcurrentLinkedQueue<PrioritizedRef>();
    private AtomicInteger pausedCount = new AtomicInteger();

    private PriorityBlockingQueue<PrioritizedRef> done = new PriorityBlockingQueue<PrioritizedRef>();

    private ThreadPoolExecutor executor;


    public Builder( String name, int poolSize ) {        
        this.name = name;
        this.executor = new ThreadPoolExecutor( poolSize, poolSize, 0L, TimeUnit.MILLISECONDS,
                                                (PriorityBlockingQueue<Runnable>)queue,
                                                new BuilderThreadFactory() );
    }
 
    public int getPending() {
        return refMap.size();
    }
    
    public void build( BuilderReference ref ) {
 
        // See if we already have a reference for this
        PrioritizedRef pr = refMap.get(ref);
        if( pr != null ) {
            if( pr.getState() == State.Pending ) {
                if( log.isTraceEnabled() ) {
                    log.trace("Already pending:" + ref);
                }
                // It's already prepared to be built
                return;
            } else {
                // It's either being processed right now or its already
                // in the done pile.  The thread calling build() is the
                // same one processing the done pile so there is no contention.
                // If we successfully change from Processing to Reprocess
                // then we can just leave.  If we don't then it means that
                // the item is already in the done pile... and we can go
                // ahead and remove it (can't be sure what it's doing and
                // anyway the data is stale) and add it again.
                // It's up to the caller not to tell us to build the same
                // thing over and over again causing it to never be done.
                if( pr.markForReprocess() ) {
                    if( log.isTraceEnabled() ) {
                        log.trace("Already marked for reprocess:" + ref);
                    }
                    // We're done here... the apply loop will eventually
                    // take care of it but there's nothing else to do since
                    // we don't know when the thread will decide to complete.
                    return;
                } else {
                    if( log.isTraceEnabled() ) {
                        log.trace("Previously processed and waiting apply:" + ref);
                    }
                    // Remove it from the done pile and just let this
                    // method continue normally
                    done.remove(pr);
                }                
            }
        } 
    
        // Always put it in the reference map before enqueueing
        // otherwise there could be a race where the thread processes
        // the item before we've added it to the map.       
        pr = new PrioritizedRef( ref );
        refMap.put( ref, pr );

        if( pausedCount.get() == 0 ) {
            if( log.isTraceEnabled() ) {
                log.trace("-executing:" + ref);
            }
            executor.execute(pr);
        } else {
            if( log.isTraceEnabled() ) {
                log.trace("-adding to paused items:" + ref);
            }
            // Just add it to the paused queue
            pausedItems.add( pr );
        }
    }
    
    public void release( BuilderReference ref ) {
    
        PrioritizedRef pr = refMap.remove(ref);
        if( pr == null ) {
            if( log.isDebugEnabled() ) {
                log.debug( "queueing for later release:" + ref );        
            }
            // We don't know anything about it.  Which means it
            // was long done being processed so we will force it
            // into the done queue for real release.
            done.put(new PrioritizedRef(State.Release, ref));
            
            // The caller should forget about ref right after making
            // this call so there should be no reason to add additional
            // book-keeping.
            return;
        }
 
        // Try to make sure it doesn't get executed
        if( pausedCount.get() == 0 ) {
            if( executor.remove(pr) ) {
                if( log.isDebugEnabled() ) {
                    log.debug( "canceled exec:" + ref );        
                }
                // Then there is nothing to release.  It was pending and
                // was never processed
                return;
            } 
        } else {
            // Just remove it from the paused queue
            if( pausedItems.remove(pr) ) {
                if( log.isDebugEnabled() ) {
                    log.debug( "canceled paused exec:" + ref );        
                }
                // Then there is also nothing to release.
                return;
            }
        }
 
        if( log.isDebugEnabled() ) {
            log.debug( "marking for release:" + ref );        
        }

        // Else it's being processed or it's already in the done
        // pile.  So we'll just force its state to release
        pr.markForRelease();
    }
 
    public boolean isPaused() {
        return pausedCount.get() > 0;
    }
    
    public void pause() {
        log.trace("pause()");
        if( pausedCount.addAndGet(1) > 1 ) {
            log.trace("already paused");
            return;
        }

        // Shuffle all of the pending items from pending to paused.
        PrioritizedRef ref;
        while( (ref = (PrioritizedRef)queue.poll()) != null ) {
            pausedItems.add(ref);
        } 
    }
    
    public void resume() {
        log.trace("resume()");
        int i = pausedCount.decrementAndGet();     
        if( i < 0 ) {
            throw new IllegalStateException("Resumed without pause.");        
        } else if( i > 0 ) {
            log.trace(i + " pending pauses remain");
            return;            
        }
        
        
        // Refresh priority and re-add paused items
        // We'll presort them into a temporary priority queue
        // so that they go into the queue in order.  Otherwise, we may
        // end up crunching on some out-of-order items right away and
        // it looks strange.  The executor's queue wouldn't have had
        // a chance to sort them any better yet because better candidates
        // haven't been added yet.
        if( pausedItems.isEmpty() ) {
            return;
        }
        PriorityQueue<PrioritizedRef> temp = new PriorityQueue<PrioritizedRef>(pausedItems.size()); 
        PrioritizedRef ref;
        while( (ref = pausedItems.poll()) != null ) {
            ref.resetPriority();
            temp.add(ref);
        }

        // Now execute them for real
        while( (ref = temp.poll()) != null ) {
            ref.resetPriority();
            executor.execute(ref);
        }
    }
    
    public int applyUpdates( int max ) {
        ArrayList<PrioritizedRef> temp = new ArrayList<PrioritizedRef>();
        int count = done.drainTo(temp, max);        
        for( PrioritizedRef pr : temp ) {
            if( log.isTraceEnabled() ) {
                log.trace("Applying updates for:" + pr.ref + "  state:" + pr.state.get());
            }
            
            // Remove the map reference 
            refMap.remove(pr.ref);
            
            // Process the item based on state        
            switch( pr.state.get() ) {
                case Done: 
                    pr.ref.apply();
                    break;
                case Release:
                    pr.ref.release();
                    break;
                case Reprocess:
                    pr.ref.apply();
                    build(pr.ref);
                    break;
                default:
                    throw new IllegalStateException("Reference is 'done' but in invalid state:" + pr.state);
            }
            count--;
        }
        return count;        
    }
    
    public void shutdown() {
        executor.shutdownNow();
    }
    
    protected void handleError( Throwable t ) {
        log.error( "Uncaught exception in worker thread", t );
    }
 
    protected enum State {
        Pending, Processing, Done, Release, Reprocess
    }
 
    protected class PrioritizedRef implements Runnable, Comparable<PrioritizedRef> {
    
        private long sequence = instanceCount.getAndIncrement();
        private BuilderReference ref;
        private AtomicReference<State> state = new AtomicReference<State>(State.Pending);        
        private int priority;

        public PrioritizedRef( BuilderReference ref ) {
            this.ref = ref;
            resetPriority();
        }

        public PrioritizedRef( State state, BuilderReference ref ) {
            this.state.set(state);
            this.ref = ref;
            resetPriority();
        }

        public State getState() {
            return state.get();
        }

        public boolean markForReprocess() {
            // Caller has already checked for pending... so we must be processing
            // or already in some done state
            return state.compareAndSet(State.Processing, State.Reprocess);
        }

        public void markForRelease() {
            // The caller has already checked for pending but the state
            // could be processing, done, or reprocess... in all cases
            // we want to mark for release.
            state.set(State.Release);
        }

        public void resetPriority() {
            this.priority = ref.getPriority();
        }

        @Override
        public int compareTo( PrioritizedRef pr ) {
            int diff = priority - pr.priority;
            if( diff == 0 )
                diff = (int)(sequence - pr.sequence);
            return diff;
        }

        @Override
        public void run() {
        
            if( !state.compareAndSet(State.Pending, State.Processing) ) {
                // We were not in a pending state.  Either we are 
                // erroneously being processed by another thread (unlikely)
                // or we were marked for release or reprocessing after
                // we'd already been picked up by the thread.  Either
                // way we will skip building 
                if( state.get() == State.Reprocess ) {
                    this.priority = -1;
                }                
                done.put(this);                      
            }

            try {            
                ref.build();
            } catch( Exception e ) {
                handleError(e);                
            } finally {            
                // Whether we are done, release, or reprocess, we are going
                // to let applyUpdates() do the magic... but in the case of
                // reprocess
                if( !state.compareAndSet(State.Processing, State.Done) ) {
                    // We reset the priorty for the case of State.Reprocess
                    // so that it gets handled sooner
                    if( state.get() == State.Reprocess ) {
                        this.priority = -1;
                    }                
                }
                done.put(this);                      
            } 
        }
    }
    
    private class BuilderThreadFactory implements ThreadFactory {
        
        @Override
        public Thread newThread( Runnable r ) {
            Thread result = Executors.defaultThreadFactory().newThread(r);
            
            String s = result.getName();
            result.setName( name + "[" + s + "]" );
            result.setDaemon(true);
            return result;            
        }
    } 
}


