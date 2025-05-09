#ifndef __RTK_HWC_LAYER_FLIP_H_
#define __RTK_HWC_LAYER_FLIP_H_
#include "VOLayerBase.h"
#include <vowindow/VOWindowVoutUtil.h>
#include "hwlayer/video/VideoLayer.h"
#include <arbiter/ResourceArbiter.h>
#include <property/HWCProperty.h>

class VOLayerFlip : public VOLayerBase,
    virtual public VOWindowVoutUtil,
    virtual public ResourceArbiter::Client,
    virtual public HWCProperty
{
public:
    enum TargetPlane {
        FLIP_V1,
        FLIP_V2,
    };

    VOLayerFlip(enum TargetPlane plane = FLIP_V1);
    virtual ~VOLayerFlip();
    virtual bool supportHwcLayer(hwc_layer_1_t * hwc_layer);
    virtual void prepare(void);
    virtual void set(void);
    virtual void dump(android::String8& buf, const char* prefix);
    virtual void setDisplayInfo(DisplayInfo * info) {VOWindowVoutUtil::setDisplayInfo(info);mDisplayInfo = info;};
    virtual void setResourceArbiter(ResourceArbiter * service) {
        ResourceArbiter::Client::setResourceArbiter(service);
    };
    virtual int ResourceEvent(int notify);
    virtual void VsyncEvent(void);
    virtual bool getHDCPInfo(void);
private:
    void openLayer(void);
    void closeLayer(void);
    VideoLayer * mHWLayer;
    bool mState;
    pthread_mutex_t     mLock;
    unsigned int        mTransform;
    int                 mTargetPlane;
    buffer_handle_t     mHandle;
    int64_t             mUpdataTimeNs;
    DisplayInfo         *mDisplayInfo;

};

#endif /* End of __RTK_HWC_LAYER_FLIP_H_ */
