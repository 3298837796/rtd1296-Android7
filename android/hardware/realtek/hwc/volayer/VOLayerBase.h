#ifndef __RTK_HWC_LAYER_BASE_H_
#define __RTK_HWC_LAYER_BASE_H_
#include <hwc_utils.h>
#include <vsync/Vsync.h>
#include <display/DisplayInfo.h>
#include <arbiter/ResourceArbiter.h>
#include <power/Power.h>
#include <vowindow/VOMixerServer.h>

class VOLayerBase : public Vsync::Client,
    virtual public VOMixerServer::Client
{
public:
    VOLayerBase(int compositionType, const char* name);
    virtual ~VOLayerBase() {};

    virtual int  getCompositionType     (void)  { return mCompositionType;};
    virtual int  getDefaultType         (void)  { return HWC_FRAMEBUFFER;};
    virtual void setHwcLayer            (hwc_layer_1_t * hwc_layer) { mHwcLayer = hwc_layer;};
    virtual hwc_layer_1_t * getHwcLayer (void)  { return mHwcLayer;};
    virtual void clearHwcLayer          (void)  { mHwcLayer = NULL;};
    virtual bool supportHwcLayer        (hwc_layer_1_t * hwc_layer);
    virtual void prepare                (void) {};
    virtual void waitAcquireFence       (void);
    virtual void set                    (void) {};
    virtual void dump                   (android::String8& buf, const char* prefix);

    virtual void setDisplayInfo         (DisplayInfo * /*info*/) {};
    virtual void setResourceArbiter     (ResourceArbiter * /*service*/) {};
    virtual void setPower               (Power * /*service*/) {};

    virtual int  getReleaseFenceFd      (void);

    virtual android::String8& getLayerName(void) { return mName;};

    /* VsyncListener API */
    virtual void VsyncEvent(void) {};
    virtual bool getHDCPInfo(void) {return false;};
private:
    int32_t         mCompositionType;
    hwc_layer_1_t * mHwcLayer;
    android::String8 mName;
};
#endif /* End of __RTK_HWC_LAYER_BASE_H_ */
