/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//#define LOG_NDEBUG 0
#define LOG_TAG "IMemory"
#if defined(__LINARO_SDK__)
#else
#include <utils/Log.h>
#endif
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>

#include <sys/types.h>
#include <sys/mman.h>

#include <binder/IMemory.h>
#include <cutils/log.h>
#include <utils/KeyedVector.h>
#include <utils/threads.h>
#include <utils/Atomic.h>
#include <binder/Parcel.h>
#include <utils/CallStack.h>

#if defined(__LINARO_SDK__)
#undef USE_ION
#else
#define USE_ION
#ifdef USE_ION
#include <ion/ion.h>
#endif
#endif
#define VERBOSE   0

namespace android {
// ---------------------------------------------------------------------------

class HeapCache : public IBinder::DeathRecipient
{
public:
    HeapCache();
    virtual ~HeapCache();

    virtual void binderDied(const wp<IBinder>& who);

    sp<IMemoryHeap> find_heap(const sp<IBinder>& binder);
    void free_heap(const sp<IBinder>& binder);
    sp<IMemoryHeap> get_heap(const sp<IBinder>& binder);
    void dump_heaps();

private:
    // For IMemory.cpp
    struct heap_info_t {
        sp<IMemoryHeap> heap;
        int32_t         count;
    };

    void free_heap(const wp<IBinder>& binder);

    Mutex mHeapCacheLock;
    KeyedVector< wp<IBinder>, heap_info_t > mHeapCache;
};

static sp<HeapCache> gHeapCache = new HeapCache();
#ifdef USE_ION
static int gIonClient = -1;
#endif

/******************************************************************************/

enum {
    HEAP_ID = IBinder::FIRST_CALL_TRANSACTION
};

class BpMemoryHeap : public BpInterface<IMemoryHeap>
{
public:
    BpMemoryHeap(const sp<IBinder>& impl);
    virtual ~BpMemoryHeap();

    virtual int getHeapID() const;
    virtual void* getBase() const;
    virtual size_t getSize() const;
    virtual uint32_t getFlags() const;
    virtual uint32_t getOffset() const;

private:
    friend class IMemory;
    friend class HeapCache;

    // for debugging in this module
    static inline sp<IMemoryHeap> find_heap(const sp<IBinder>& binder) {
        return gHeapCache->find_heap(binder);
    }
    static inline void free_heap(const sp<IBinder>& binder) {
        gHeapCache->free_heap(binder);
    }
    static inline sp<IMemoryHeap> get_heap(const sp<IBinder>& binder) {
        return gHeapCache->get_heap(binder);
    }
    static inline void dump_heaps() {
        gHeapCache->dump_heaps();
    }

    void assertMapped() const;
    void assertReallyMapped() const;

    mutable volatile int32_t mHeapId;
    mutable void*       mBase;
    mutable size_t      mSize;
    mutable uint32_t    mFlags;
    mutable uint32_t    mOffset;
    mutable bool        mRealHeap;
    mutable Mutex       mLock;
};

// ----------------------------------------------------------------------------

enum {
    GET_MEMORY = IBinder::FIRST_CALL_TRANSACTION
};

class BpMemory : public BpInterface<IMemory>
{
public:
    BpMemory(const sp<IBinder>& impl);
    virtual ~BpMemory();
    virtual sp<IMemoryHeap> getMemory(ssize_t* offset=0, size_t* size=0) const;

private:
    mutable sp<IMemoryHeap> mHeap;
    mutable ssize_t mOffset;
    mutable size_t mSize;
};

/******************************************************************************/

void* IMemory::fastPointer(const sp<IBinder>& binder, ssize_t offset) const
{
    sp<IMemoryHeap> realHeap = BpMemoryHeap::get_heap(binder);
    void* const base = realHeap->base();
    if (base == MAP_FAILED)
        return 0;
    return static_cast<char*>(base) + offset;
}

void* IMemory::pointer() const {
    ssize_t offset;
    sp<IMemoryHeap> heap = getMemory(&offset);
    void* const base = heap!=0 ? heap->base() : MAP_FAILED;
    if (base == MAP_FAILED)
        return 0;
    return static_cast<char*>(base) + offset;
}

size_t IMemory::size() const {
    size_t size;
    getMemory(NULL, &size);
    return size;
}

ssize_t IMemory::offset() const {
    ssize_t offset;
    getMemory(&offset);
    return offset;
}

/******************************************************************************/

BpMemory::BpMemory(const sp<IBinder>& impl)
    : BpInterface<IMemory>(impl), mOffset(0), mSize(0)
{
}

BpMemory::~BpMemory()
{
}

sp<IMemoryHeap> BpMemory::getMemory(ssize_t* offset, size_t* size) const
{
    if (mHeap == 0) {
        Parcel data, reply;
        data.writeInterfaceToken(IMemory::getInterfaceDescriptor());
        if (remote()->transact(GET_MEMORY, data, &reply) == NO_ERROR) {
            sp<IBinder> heap = reply.readStrongBinder();
            ssize_t o = reply.readInt32();
            size_t s = reply.readInt32();
            if (heap != 0) {
                mHeap = interface_cast<IMemoryHeap>(heap);
                if (mHeap != 0) {
                    size_t heapSize = mHeap->getSize();
                    if (s <= heapSize
                            && o >= 0
                            && (static_cast<size_t>(o) <= heapSize - s)) {
                        mOffset = o;
                        mSize = s;
                    } else {
                        // Hm.
                        android_errorWriteWithInfoLog(0x534e4554,
                            "26877992", -1, NULL, 0);
                        mOffset = 0;
                        mSize = 0;
                    }
                }
            }
        }
    }
    if (offset) *offset = mOffset;
    if (size) *size = mSize;
    return (mSize > 0) ? mHeap : 0;
}

// ---------------------------------------------------------------------------

IMPLEMENT_META_INTERFACE(Memory, "android.utils.IMemory");

BnMemory::BnMemory() {
}

BnMemory::~BnMemory() {
}

status_t BnMemory::onTransact(
    uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
{
    switch(code) {
        case GET_MEMORY: {
            CHECK_INTERFACE(IMemory, data, reply);
            ssize_t offset;
            size_t size;
            reply->writeStrongBinder( IInterface::asBinder(getMemory(&offset, &size)) );
            reply->writeInt32(offset);
            reply->writeInt32(size);
            return NO_ERROR;
        } break;
        default:
            return BBinder::onTransact(code, data, reply, flags);
    }
}


/******************************************************************************/

BpMemoryHeap::BpMemoryHeap(const sp<IBinder>& impl)
    : BpInterface<IMemoryHeap>(impl),
        mHeapId(-1), mBase(MAP_FAILED), mSize(0), mFlags(0), mOffset(0), mRealHeap(false)
{
}

BpMemoryHeap::~BpMemoryHeap() {
    if (mHeapId != -1) {
        close(mHeapId);
        if (mRealHeap) {
            // by construction we're the last one
            if (mBase != MAP_FAILED) {
                sp<IBinder> binder = IInterface::asBinder(this);

                if (VERBOSE) {
                    ALOGD("UNMAPPING binder=%p, heap=%p, size=%zu, fd=%d",
                            binder.get(), this, mSize, mHeapId);
                    CallStack stack(LOG_TAG);
                }

                munmap(mBase, mSize);
            }
        } else {
            // remove from list only if it was mapped before
            sp<IBinder> binder = IInterface::asBinder(this);
            free_heap(binder);
        }
#if 0//def USE_ION //don't open/close global ion client for each MemoryHeap
	//TODO: reference count
        if(gIonClient >= 0)
            ion_close(gIonClient);
#endif
    }
}

void BpMemoryHeap::assertMapped() const
{
    if (mHeapId == -1) {
        sp<IBinder> binder(IInterface::asBinder(const_cast<BpMemoryHeap*>(this)));
        sp<BpMemoryHeap> heap(static_cast<BpMemoryHeap*>(find_heap(binder).get()));
        heap->assertReallyMapped();
        if (heap->mBase != MAP_FAILED) {
            Mutex::Autolock _l(mLock);
            if (mHeapId == -1) {
                mBase   = heap->mBase;
                mSize   = heap->mSize;
                mOffset = heap->mOffset;
#if 1 //added by charles
                mFlags  = heap->mFlags;
#endif
                android_atomic_write( dup( heap->mHeapId ), &mHeapId );
                //if(heap->mFlags & USE_ION_FD) ALOGD("assertMapped: mHeapId:%d", mHeapId);
            }
        } else {
            // something went wrong
            free_heap(binder);
        }
    }
}

void BpMemoryHeap::assertReallyMapped() const
{
    if (mHeapId == -1) {

        // remote call without mLock held, worse case scenario, we end up
        // calling transact() from multiple threads, but that's not a problem,
        // only mmap below must be in the critical section.

        Parcel data, reply;
        data.writeInterfaceToken(IMemoryHeap::getInterfaceDescriptor());
        status_t err = remote()->transact(HEAP_ID, data, &reply);
        int parcel_fd = reply.readFileDescriptor();
        ssize_t size = reply.readInt32();
        uint32_t flags = reply.readInt32();
        uint32_t offset = reply.readInt32();

#ifdef USE_ION
        //open ion device first, otherwise mmap will fail
        if (flags & USE_ION_FD && gIonClient == -1) {
            gIonClient = ion_open();
            ALOGE_IF(gIonClient < 0, "BpMemoryHeap : ion client creation error");
        }
#endif

        ALOGE_IF(err, "binder=%p transaction failed fd=%d, size=%zd, err=%d (%s)",
                IInterface::asBinder(this).get(),
                parcel_fd, size, err, strerror(-err));

        Mutex::Autolock _l(mLock);
        if (mHeapId == -1) {
            int fd = dup( parcel_fd );
            ALOGE_IF(fd==-1, "cannot dup fd=%d, size=%zd, err=%d (%s)",
                    parcel_fd, size, err, strerror(errno));

            int access = PROT_READ;
            if (!(flags & READ_ONLY)) {
                access |= PROT_WRITE;
            }

            mRealHeap = true;
            mBase = mmap(0, size, access, MAP_SHARED, fd, offset);
            if (mBase == MAP_FAILED) {
                ALOGE("cannot map BpMemoryHeap (binder=%p), size=%zd, fd=%d (%s)",
                        IInterface::asBinder(this).get(), size, fd, strerror(errno));
                close(fd);
            } else {
                mSize = size;
                mFlags = flags;
                mOffset = offset;
                android_atomic_write(fd, &mHeapId);
                //if(mFlags & USE_ION_FD) ALOGD("assertReallyMapped: mHeapId:%d mFlags:%x", mHeapId, mFlags);
#if 0 //used to get physical address of ion memory by shared fd
                unsigned long addr;
                unsigned int size;
                struct ion_handle *handle;
                ion_import(gIonClient, mHeapId, &handle);
                ion_phys(gIonClient, handle, &addr, &size);
                ALOGD("get ion physical addr:%lx size:%d", addr, size);
#endif
            }
        }
    }
}

int BpMemoryHeap::getHeapID() const {
    assertMapped();
    return mHeapId;
}

void* BpMemoryHeap::getBase() const {
    assertMapped();
    return mBase;
}

size_t BpMemoryHeap::getSize() const {
    assertMapped();
    return mSize;
}

uint32_t BpMemoryHeap::getFlags() const {
    assertMapped();
    return mFlags;
}

uint32_t BpMemoryHeap::getOffset() const {
    assertMapped();
    return mOffset;
}

// ---------------------------------------------------------------------------

IMPLEMENT_META_INTERFACE(MemoryHeap, "android.utils.IMemoryHeap");

BnMemoryHeap::BnMemoryHeap() {
}

BnMemoryHeap::~BnMemoryHeap() {
}

status_t BnMemoryHeap::onTransact(
        uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
{
    switch(code) {
       case HEAP_ID: {
            CHECK_INTERFACE(IMemoryHeap, data, reply);
            reply->writeFileDescriptor(getHeapID());
            reply->writeInt32(getSize());
            reply->writeInt32(getFlags());
            reply->writeInt32(getOffset());
            return NO_ERROR;
        } break;
        default:
            return BBinder::onTransact(code, data, reply, flags);
    }
}

/*****************************************************************************/

HeapCache::HeapCache()
    : DeathRecipient()
{
}

HeapCache::~HeapCache()
{
}

void HeapCache::binderDied(const wp<IBinder>& binder)
{
    //ALOGD("binderDied binder=%p", binder.unsafe_get());
    free_heap(binder);
}

sp<IMemoryHeap> HeapCache::find_heap(const sp<IBinder>& binder)
{
    Mutex::Autolock _l(mHeapCacheLock);
    ssize_t i = mHeapCache.indexOfKey(binder);
    if (i>=0) {
        heap_info_t& info = mHeapCache.editValueAt(i);
        ALOGD_IF(VERBOSE,
                "found binder=%p, heap=%p, size=%zu, fd=%d, count=%d",
                binder.get(), info.heap.get(),
                static_cast<BpMemoryHeap*>(info.heap.get())->mSize,
                static_cast<BpMemoryHeap*>(info.heap.get())->mHeapId,
                info.count);
        android_atomic_inc(&info.count);
        return info.heap;
    } else {
        heap_info_t info;
        info.heap = interface_cast<IMemoryHeap>(binder);
        info.count = 1;
        //ALOGD("adding binder=%p, heap=%p, count=%d",
        //      binder.get(), info.heap.get(), info.count);
        mHeapCache.add(binder, info);
        return info.heap;
    }
}

void HeapCache::free_heap(const sp<IBinder>& binder)  {
    free_heap( wp<IBinder>(binder) );
}

void HeapCache::free_heap(const wp<IBinder>& binder)
{
    sp<IMemoryHeap> rel;
    {
        Mutex::Autolock _l(mHeapCacheLock);
        ssize_t i = mHeapCache.indexOfKey(binder);
        if (i>=0) {
            heap_info_t& info(mHeapCache.editValueAt(i));
            int32_t c = android_atomic_dec(&info.count);
            if (c == 1) {
                ALOGD_IF(VERBOSE,
                        "removing binder=%p, heap=%p, size=%zu, fd=%d, count=%d",
                        binder.unsafe_get(), info.heap.get(),
                        static_cast<BpMemoryHeap*>(info.heap.get())->mSize,
                        static_cast<BpMemoryHeap*>(info.heap.get())->mHeapId,
                        info.count);
                rel = mHeapCache.valueAt(i).heap;
                mHeapCache.removeItemsAt(i);
            }
        } else {
            ALOGE("free_heap binder=%p not found!!!", binder.unsafe_get());
        }
    }
}

sp<IMemoryHeap> HeapCache::get_heap(const sp<IBinder>& binder)
{
    sp<IMemoryHeap> realHeap;
    Mutex::Autolock _l(mHeapCacheLock);
    ssize_t i = mHeapCache.indexOfKey(binder);
    if (i>=0)   realHeap = mHeapCache.valueAt(i).heap;
    else        realHeap = interface_cast<IMemoryHeap>(binder);
    return realHeap;
}

void HeapCache::dump_heaps()
{
    Mutex::Autolock _l(mHeapCacheLock);
    int c = mHeapCache.size();
    for (int i=0 ; i<c ; i++) {
        const heap_info_t& info = mHeapCache.valueAt(i);
        BpMemoryHeap const* h(static_cast<BpMemoryHeap const *>(info.heap.get()));
        ALOGD("hey=%p, heap=%p, count=%d, (fd=%d, base=%p, size=%zu)",
                mHeapCache.keyAt(i).unsafe_get(),
                info.heap.get(), info.count,
                h->mHeapId, h->mBase, h->mSize);
    }
}


// ---------------------------------------------------------------------------
}; // namespace android
