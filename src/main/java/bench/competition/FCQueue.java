/******************************************************************************

 Copyright (c) 2010 Itai Incze

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.

 Except as contained in this notice, the name(s) of the above copyright holders
 shall not be used in advertising or otherwise to promote the sale,
 use or other dealings in this Software without prior written authorization.

  ---------------------------------------------------------------------------

 Source : http://mcg.cs.tau.ac.il/projects/flat-combining
 Version : 0.1
 Author : Itai Incze
 Release Date : 21 Aug 2010
 Description : A concurrent queue implementation based on Flat Combining as
               described in the paper "Flat Combining and the
               Synchronization-Parallelism Tradeoff".

******************************************************************************/

package chemistry.bench;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class FCQueue
{
    // Maximum participating threads
    final int MAX_THREADS = 512;
    
    static class CombiningNode
    {
        volatile boolean is_linked;
        int last_request_timestamp;
        // comb_list_head CAS will perform the write for this 
        CombiningNode next;
        volatile boolean is_request_valid;
        // membar on item and is_consumer is committed by a write to is_request_valid
        boolean is_consumer;
        Object item;

        CombiningNode()
        {
            is_linked = false;
            next = null;
            is_request_valid = false;
        }
    }

    AtomicInteger fc_lock;
    // used to gather combined enqueued items
    Object[] combined_pushed_items;

    volatile int current_timestamp = 0;

    private ThreadLocal combining_node = new ThreadLocal()
    {
        @Override
        protected Object initialValue() {
            return new CombiningNode();
        }
    };

    volatile CombiningNode comb_list_head;
    // For compareAndSet on the _req_list_head
    final private static AtomicReferenceFieldUpdater comb_list_head_updater =
            AtomicReferenceFieldUpdater.newUpdater(FCQueue.class,CombiningNode.class, "comb_list_head");
    
    static class QueueFatNode
    {
        Object items[];
        int items_left;
        QueueFatNode next;
    }

    volatile QueueFatNode queue_head, queue_tail;

    FCQueue()
    {
        combined_pushed_items = new Object[MAX_THREADS];
        fc_lock = new AtomicInteger(0);
        queue_head = new QueueFatNode();
        queue_tail = queue_head;
        queue_head.next = null;
        queue_head.items_left = 0;
    }

    final int COMBINING_NODE_TIMEOUT = 10000;
    final int COMBINING_NODE_TIMEOUT_CHECK_FREQUENCY = 100;
    final int MAX_COMBINING_ROUNDS = 32;

    void doFlatCombining(CombiningNode combiner_thread_node)
    {
        int combining_rounds = 0;
        int num_pushed_items = 0;
        CombiningNode cur_comb_node = null;
        CombiningNode last_combining_node =  null;

        // advance timestamp and sample volatile variables to local variables for reading speed
        int local_current_timestamp = ++current_timestamp;
        QueueFatNode local_queue_head = queue_head;

        boolean check_timestamps = (local_current_timestamp % COMBINING_NODE_TIMEOUT_CHECK_FREQUENCY == 0);
        boolean have_work = false;

        while (true)
        {
            // initialize for a new round
            num_pushed_items = 0;
            cur_comb_node = comb_list_head;
            last_combining_node = cur_comb_node;
            have_work = false;

            while (cur_comb_node != null)
            {
                if (!cur_comb_node.is_request_valid)
                {
                    // after manipulating is_linked the owner thread can change next so we need to save it first
                    CombiningNode next_node = cur_comb_node.next;

                    // take the node out if its not the first one
                    // (we're letting the first one go to avoid CASes)
                    if ((check_timestamps) &&
                        (cur_comb_node != comb_list_head) &&
                        (local_current_timestamp - cur_comb_node.last_request_timestamp > COMBINING_NODE_TIMEOUT))
                    {
                        last_combining_node.next = next_node;
                        cur_comb_node.is_linked = false;
                    }
                    cur_comb_node = next_node;
                    continue;
                }

                have_work = true;

                // update combining node last use timestamp
                cur_comb_node.last_request_timestamp = local_current_timestamp;

                if (cur_comb_node.is_consumer)
                {
                    boolean consumer_satisfied = false;
                    // check queue first
                    while ((local_queue_head.next != null) && !consumer_satisfied)
                    {
                        QueueFatNode head_next = local_queue_head.next;
                        if (head_next.items_left == 0)
                        {
                            local_queue_head = head_next;
                        }
                        else
                        {
                            head_next.items_left--;
                            cur_comb_node.item = head_next.items[head_next.items_left];
                            consumer_satisfied = true;
                        }
                    }
                    
                    // if queue is empty, check current pass
                    if ((!consumer_satisfied) && (num_pushed_items > 0))
                    {
                        num_pushed_items--;
                        cur_comb_node.item = combined_pushed_items[num_pushed_items];
                        consumer_satisfied = true;
                    }

                    if (!consumer_satisfied)
                    {
                        // queue empty
                        cur_comb_node.item = null;
                    }
                }
                else
                {
                    combined_pushed_items[num_pushed_items] = cur_comb_node.item;
                    num_pushed_items++;
                }

                // requesting thread is released
                cur_comb_node.is_request_valid = false;

                // next node
                last_combining_node = cur_comb_node;
                cur_comb_node = cur_comb_node.next;
            }

            // pushed items needs to go into the queue
            if (num_pushed_items > 0)
            {
                QueueFatNode new_node = new QueueFatNode();
                new_node.items_left = num_pushed_items;
                new_node.items = new Object[num_pushed_items];
                System.arraycopy(combined_pushed_items, 0, new_node.items, 0, num_pushed_items);
                new_node.next = null;
                queue_tail.next = new_node;
                queue_tail = new_node;
            }            

            combining_rounds++;
            if ((!have_work) ||
                (combining_rounds >= MAX_COMBINING_ROUNDS))
            {
                // no more rounds needed

                // Update queue_head.
                // This membar flushes write queue so it also finalize changes made to the queue nodes
                queue_head = local_queue_head;
                
                return;
            }
        }
    }

    private void link_in_combining(CombiningNode cn)
    {
        while (true)
        {
            // snapshot the list head
            CombiningNode cur_head = comb_list_head;
            cn.next = cur_head;
            
            // try to insert the node
            if (comb_list_head == cur_head)
            {
                if (comb_list_head_updater.compareAndSet(this, cn.next, cn))
                {
                    return;
                }
            }
        }
    }

    final int NUM_ROUNDS_IS_LINKED_CHECK_FREQUENCY = 100;
    
    private void wait_until_fulfilled(CombiningNode comb_node)
    {
        int rounds = 0;

        while (true)
        {
            // make sure the combining node is in the list
            if ((rounds % NUM_ROUNDS_IS_LINKED_CHECK_FREQUENCY == 0) &&
                (!comb_node.is_linked))
            {
                comb_node.is_linked = true;
                link_in_combining(comb_node);
            }

            if (fc_lock.get() == 0)
            {
                if (fc_lock.compareAndSet(0, 1))
                {
                    // combiner
                    doFlatCombining(comb_node);
                    fc_lock.set(0);
                }
            }

            if (!comb_node.is_request_valid)
            {
                return;
            }

            rounds++;
        }
    }

    public boolean enqueue(Object value){
        CombiningNode comb_node = (CombiningNode)combining_node.get();
        comb_node.is_consumer = false;
        comb_node.item = value;

        comb_node.is_request_valid = true;

        wait_until_fulfilled(comb_node);
        return true;
    }

    public Object dequeue(){
        CombiningNode comb_node = (CombiningNode)combining_node.get();
        comb_node.is_consumer = true;        

        comb_node.is_request_valid = true;
        
        wait_until_fulfilled(comb_node);
        return comb_node.item;
    }
}

