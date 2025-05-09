/*
 * (C) Copyright 2001
 * Denis Peter, MPL AG Switzerland
 *
 * See file CREDITS for list of people who contributed to this
 * project.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	 See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston,
 * MA 02111-1307 USA
 *
 * Note: Part of this code has been derived from linux
 *
 */
#ifndef _USB_H_
#define _USB_H_

#include <usb_defs.h>
#include <linux/usb/ch9.h>
#include <stddef.h>
#include <debug.h>
#include <platform/gpio.h>
#define mdelay(_msec)      spin((_msec) * 1000)
#define udelay(_usec)      spin((_usec))

#define CONFIG_SYS_CACHELINE_SIZE	64
#define CONFIG_USB_EHCI
#define CONFIG_USB_XHCI

#define USB_MAX_CONTROLLER_COUNT 4

//#define debug(fmt, args...)       printf(fmt, ##args)
#define debug(fmt, args...)      do{}while(0) 


#define min(X, Y)                               \
        ({ typeof (X) __x = (X);                \
                typeof (Y) __y = (Y);           \
                (__x < __y) ? __x : __y; })

#define max(X, Y)                               \
        ({ typeof (X) __x = (X);                \
                typeof (Y) __y = (Y);           \
                (__x > __y) ? __x : __y; })

#define min3(a, b, c)        min(min(a, b), c)


#define __arch_getb(a)                  (*(volatile unsigned char *)(a))
#define __arch_getw(a)                  (*(volatile unsigned short *)(a))
#define __arch_getl(a)                  (*(volatile unsigned int *)(a))

#define __arch_putb(v,a)                (*(volatile unsigned char *)(a) = (v))
#define __arch_putw(v,a)                (*(volatile unsigned short *)(a) = (v))
#define __arch_putl(v,a)                (*(volatile unsigned int *)(a) = (v))



#define __raw_writeb(v,a)       __arch_putb(v,a)
#define __raw_writew(v,a)       __arch_putw(v,a)
#define __raw_writel(v,a)       __arch_putl(v,a)

#define __raw_readb(a)          __arch_getb(a)
#define __raw_readw(a)          __arch_getw(a)
#define __raw_readl(a)          __arch_getl(a)

#define EFUSE_ADDR              (0x980171d8)
#define CHIP_INFO1              (0x98007028)

#define SB2_CHIP_INFO       (0x9801a204)
#define RTK1295_CPU_ID          0x00000000
#define RTK1294_CPU_ID          0x00000001
#define RTK1296_CPU_ID          0x00000002

#define RTK129x_CPU_MASK        0x00000003
#define RTK1296_CPU_MASK        0x00000800
#define SOC_REV_A               (0x0)
#define SOC_REV_B               (0x1)
#define SOC_REV_C               (0x2)

#define RTD129x_CHIP_REVISION_A00 0x00000000
#define RTD129x_CHIP_REVISION_A01 0x00010000
#define RTD129x_CHIP_REVISION_B00 0x00020000


#define uswap_16(x) \
        ((((x) & 0xff00) >> 8) | \
         (((x) & 0x00ff) << 8))
#define uswap_32(x) \
        ((((x) & 0xff000000) >> 24) | \
         (((x) & 0x00ff0000) >>  8) | \
         (((x) & 0x0000ff00) <<  8) | \
         (((x) & 0x000000ff) << 24))
#define _uswap_64(x, sfx) \
        ((((x) & 0xff00000000000000##sfx) >> 56) | \
         (((x) & 0x00ff000000000000##sfx) >> 40) | \
         (((x) & 0x0000ff0000000000##sfx) >> 24) | \
         (((x) & 0x000000ff00000000##sfx) >>  8) | \
         (((x) & 0x00000000ff000000##sfx) <<  8) | \
         (((x) & 0x0000000000ff0000##sfx) << 24) | \
         (((x) & 0x000000000000ff00##sfx) << 40) | \
         (((x) & 0x00000000000000ff##sfx) << 56))

#if defined(__GNUC__)
# define uswap_64(x) _uswap_64(x, ull)
#else
# define uswap_64(x) _uswap_64(x, )
#endif


#if BYTE_ORDER == LITTLE_ENDIAN
# define cpu_to_le16(x)         (x)
# define cpu_to_le32(x)         (x)
# define cpu_to_le64(x)         (x)
# define le16_to_cpu(x)         (x)
# define le32_to_cpu(x)         (x)
# define le64_to_cpu(x)         (x)
# define cpu_to_be16(x)         uswap_16(x)
# define cpu_to_be32(x)         uswap_32(x)
# define cpu_to_be64(x)         uswap_64(x)
# define be16_to_cpu(x)         uswap_16(x)
# define be32_to_cpu(x)         uswap_32(x)
# define be64_to_cpu(x)         uswap_64(x)

# define le16_to_cpus(x) do {} while (0)
#else
# define cpu_to_le16(x)         LE16(x)
# define cpu_to_le32(x)         LE32(x)
# define cpu_to_le64(x)         LE64(x)
# define le16_to_cpu(x)         LE16(x)
# define le32_to_cpu(x)         LE32(x)
# define le64_to_cpu(x)         LE64(x)
#endif

/*
 * The EHCI spec says that we must align to at least 32 bytes.  However,
 * some platforms require larger alignment.
 */
#define ARCH_DMA_MINALIGN    64    //hcy test
#if ARCH_DMA_MINALIGN > 32
#define USB_DMA_MINALIGN	ARCH_DMA_MINALIGN
#else
#define USB_DMA_MINALIGN	32
#endif

/* Everything is aribtrary */
#define USB_ALTSETTINGALLOC		4
#define USB_MAXALTSETTING		128	/* Hard limit */

#define USB_MAX_DEVICE			32
#define USB_MAXCONFIG			8
#define USB_MAXINTERFACES		8
#define USB_MAXENDPOINTS		16
#define USB_MAXCHILDREN			8	/* This is arbitrary */
#define USB_MAX_HUB			16

#define USB_CNTL_TIMEOUT 100 /* 100ms timeout */

/*
 * This is the timeout to allow for submitting an urb in ms. We allow more
 * time for a BULK device to react - some are slow.
 */
#define USB_TIMEOUT_MS(pipe) (usb_pipebulk(pipe) ? 5000 : 1000)

/* device request (setup) */
struct devrequest {
	unsigned char	requesttype;
	unsigned char	request;
	unsigned short	value;
	unsigned short	index;
	unsigned short	length;
} __attribute__ ((packed));

/* Interface */
struct usb_interface {
	struct usb_interface_descriptor desc;

	unsigned char	no_of_ep;
	unsigned char	num_altsetting;
	unsigned char	act_altsetting;

	struct usb_endpoint_descriptor ep_desc[USB_MAXENDPOINTS];
	/*
	 * Super Speed Device will have Super Speed Endpoint
	 * Companion Descriptor  (section 9.6.7 of usb 3.0 spec)
	 * Revision 1.0 June 6th 2011
	 */
	struct usb_ss_ep_comp_descriptor ss_ep_comp_desc[USB_MAXENDPOINTS];
} __attribute__ ((packed));

/* Configuration information.. */
struct usb_config {
	struct usb_config_descriptor desc;

	unsigned char	no_of_if;	/* number of interfaces */
	struct usb_interface if_desc[USB_MAXINTERFACES];
} __attribute__ ((packed));

enum {
	/* Maximum packet size; encoded as 0,1,2,3 = 8,16,32,64 */
	PACKET_SIZE_8   = 0,
	PACKET_SIZE_16  = 1,
	PACKET_SIZE_32  = 2,
	PACKET_SIZE_64  = 3,
};

struct controller {
	int ctrl_type;
	void *unsed; //Note we only use ctrl_type
};

enum {
	CTRL_TYPE_UNKNOW = 0,
	CTRL_TYPE_EHCI   = 1,
	CTRL_TYPE_XHCI   = 2,
};

struct usb_device {
	int	devnum;			/* Device number on USB bus */
	int	speed;			/* full/low/high */
	char	mf[32];			/* manufacturer */
	char	prod[32];		/* product */
	char	serial[32];		/* serial number */

	/* Maximum packet size; one of: PACKET_SIZE_* */
	int maxpacketsize;
	/* one bit for each endpoint ([0] = IN, [1] = OUT) */
	unsigned int toggle[2];
	/* endpoint halts; one bit per endpoint # & direction;
	 * [0] = IN, [1] = OUT
	 */
	unsigned int halted[2];
	int epmaxpacketin[16];		/* INput endpoint specific maximums */
	int epmaxpacketout[16];		/* OUTput endpoint specific maximums */

	int configno;			/* selected config number */
	/* Device Descriptor */
	struct usb_device_descriptor descriptor
		__attribute__((aligned(ARCH_DMA_MINALIGN)));
	struct usb_config config; /* config descriptor */

	int have_langid;		/* whether string_langid is valid yet */
	int string_langid;		/* language ID for strings */
	int (*irq_handle)(struct usb_device *dev);
	unsigned long irq_status;
	int irq_act_len;		/* transfered bytes */
	void *privptr;
	/*
	 * Child devices -  if this is a hub device
	 * Each instance needs its own set of data structures.
	 */
	unsigned long status;
	int act_len;			/* transfered bytes */
	int maxchild;			/* Number of ports if hub */
	int portnr;
	struct usb_device *parent;
	struct usb_device *children[USB_MAXCHILDREN];

	void *controller;		/* hardware controller private data */
	/* slot_id - for xHCI enabled devices */
	unsigned int slot_id;

	char level; //hcy added
	int route; //hcy added 
};



inline int get_cpu_id(void) {
        int cpu_id = RTK1295_CPU_ID;
        if ((__raw_readl((volatile u32*)(EFUSE_ADDR)) & RTK129x_CPU_MASK) == RTK1294_CPU_ID) {
                cpu_id = RTK1294_CPU_ID;
        } else if ((__raw_readl((volatile u32*)(CHIP_INFO1)) & RTK1296_CPU_MASK)) {
                cpu_id = RTK1296_CPU_ID;
        }
        return cpu_id;
}
//#define get_cpu_revision()     __raw_readl((volatile u32*)(SB2_IO_ADDR(CHIP_REV)))

inline u32 get_rtd129x_cpu_revision(void) {
        u32 val = __raw_readl((volatile u32*)SB2_CHIP_INFO);
        return val;
}


/**********************************************************************
 * this is how the lowlevel part communicate with the outer world
 */
#if defined(CONFIG_USB_UHCI) || defined(CONFIG_USB_OHCI) || \
	defined(CONFIG_USB_EHCI) || defined(CONFIG_USB_OHCI_NEW) || \
	defined(CONFIG_USB_SL811HS) || defined(CONFIG_USB_ISP116X_HCD) || \
	defined(CONFIG_USB_R8A66597_HCD) || defined(CONFIG_USB_DAVINCI) || \
	defined(CONFIG_USB_OMAP3) || defined(CONFIG_USB_DA8XX) || \
	defined(CONFIG_USB_BLACKFIN) || defined(CONFIG_USB_AM35X) || \
	defined(CONFIG_USB_MUSB_DSPS) || defined(CONFIG_USB_MUSB_AM35X) || \
	defined(CONFIG_USB_MUSB_OMAP2PLUS) || defined(CONFIG_USB_XHCI)

int usb_lowlevel_init(int index, void **controller);
int usb_lowlevel_stop(void);

int submit_bulk_msg(struct usb_device *dev, unsigned long pipe,
			void *buffer, int transfer_len);
int submit_control_msg(struct usb_device *dev, unsigned long pipe, void *buffer,
			int transfer_len, struct devrequest *setup);
int submit_int_msg(struct usb_device *dev, unsigned long pipe, void *buffer,
			int transfer_len, int interval);

/* Defines */
#define USB_UHCI_VEND_ID	0x8086
#define USB_UHCI_DEV_ID		0x7112

/*
 * PXA25x can only act as USB device. There are drivers
 * which works with USB CDC gadgets implementations.
 * Some of them have common routines which can be used
 * in boards init functions e.g. udc_disconnect() used for
 * forced device disconnection from host.
 */
#elif defined(CONFIG_USB_GADGET_PXA2XX)

extern void udc_disconnect(void);

#else
//#error USB Lowlevel not defined
#endif

#ifdef CONFIG_USB_RTKBT
int usb_bt_scan(int mode);
#endif

#ifdef CONFIG_USB_STORAGE

#define USB_MAX_STOR_DEV 5
block_dev_desc_t *usb_stor_get_dev(int index);
int usb_stor_scan(int mode);
int usb_stor_info(void);

#endif

#ifdef CONFIG_USB_HOST_ETHER

#define USB_MAX_ETH_DEV 5
int usb_host_eth_scan(int mode);

#endif

#ifdef CONFIG_USB_KEYBOARD

int drv_usb_kbd_init(void);
int usb_kbd_deregister(void);

#endif
/* routines */
int usb_init(void); /* initialize the USB Controller */
int usb_stop(void); /* stop the USB Controller */

/* for host setting init */
int usb_host_controller_init(void);

int usb_set_protocol(struct usb_device *dev, int ifnum, int protocol);
int usb_set_idle(struct usb_device *dev, int ifnum, int duration,
			int report_id);
struct usb_device *usb_get_dev_index(int index);
int usb_control_msg(struct usb_device *dev, unsigned int pipe,
			unsigned char request, unsigned char requesttype,
			unsigned short value, unsigned short index,
			void *data, unsigned short size, int timeout);
int usb_bulk_msg(struct usb_device *dev, unsigned int pipe,
			void *data, int len, int *actual_length, int timeout);
#if 1 // add by cfyeh
int usb_interrupt_msg(struct usb_device *dev, unsigned int pipe,
			void *data, int len, int *actual_length, int timeout);
#endif // add by cfyeh
int usb_submit_int_msg(struct usb_device *dev, unsigned long pipe,
			void *buffer, int transfer_len, int interval);
int usb_disable_asynch(int disable);
int usb_maxpacket(struct usb_device *dev, unsigned long pipe);
int usb_get_configuration_no(struct usb_device *dev, unsigned char *buffer,
				int cfgno);
int usb_get_report(struct usb_device *dev, int ifnum, unsigned char type,
			unsigned char id, void *buf, int size);
int usb_get_class_descriptor(struct usb_device *dev, int ifnum,
			unsigned char type, unsigned char id, void *buf,
			int size);
int usb_clear_halt(struct usb_device *dev, int pipe);
int usb_string(struct usb_device *dev, int index, char *buf, size_t size);
int usb_set_interface(struct usb_device *dev, int interface, int alternate);

/* big endian -> little endian conversion */
/* some CPUs are already little endian e.g. the ARM920T */
#define __swap_16(x) \
	({ unsigned short x_ = (unsigned short)x; \
	 (unsigned short)( \
		((x_ & 0x00FFU) << 8) | ((x_ & 0xFF00U) >> 8)); \
	})
#define __swap_32(x) \
	({ unsigned long x_ = (unsigned long)x; \
	 (unsigned long)( \
		((x_ & 0x000000FFUL) << 24) | \
		((x_ & 0x0000FF00UL) <<	 8) | \
		((x_ & 0x00FF0000UL) >>	 8) | \
		((x_ & 0xFF000000UL) >> 24)); \
	})

#ifdef __LITTLE_ENDIAN
# define swap_16(x) (x)
# define swap_32(x) (x)
#else
# define swap_16(x) __swap_16(x)
# define swap_32(x) __swap_32(x)
#endif

/*
 * Calling this entity a "pipe" is glorifying it. A USB pipe
 * is something embarrassingly simple: it basically consists
 * of the following information:
 *  - device number (7 bits)
 *  - endpoint number (4 bits)
 *  - current Data0/1 state (1 bit)
 *  - direction (1 bit)
 *  - speed (2 bits)
 *  - max packet size (2 bits: 8, 16, 32 or 64)
 *  - pipe type (2 bits: control, interrupt, bulk, isochronous)
 *
 * That's 18 bits. Really. Nothing more. And the USB people have
 * documented these eighteen bits as some kind of glorious
 * virtual data structure.
 *
 * Let's not fall in that trap. We'll just encode it as a simple
 * unsigned int. The encoding is:
 *
 *  - max size:		bits 0-1	(00 = 8, 01 = 16, 10 = 32, 11 = 64)
 *  - direction:	bit 7		(0 = Host-to-Device [Out],
 *					(1 = Device-to-Host [In])
 *  - device:		bits 8-14
 *  - endpoint:		bits 15-18
 *  - Data0/1:		bit 19
 *  - pipe type:	bits 30-31	(00 = isochronous, 01 = interrupt,
 *					 10 = control, 11 = bulk)
 *
 * Why? Because it's arbitrary, and whatever encoding we select is really
 * up to us. This one happens to share a lot of bit positions with the UHCI
 * specification, so that much of the uhci driver can just mask the bits
 * appropriately.
 */
/* Create various pipes... */
#define create_pipe(dev,endpoint) \
		(((dev)->devnum << 8) | ((endpoint) << 15) | \
		(dev)->maxpacketsize)
#define default_pipe(dev) ((dev)->speed << 26)

#define usb_sndctrlpipe(dev, endpoint)	((PIPE_CONTROL << 30) | \
					 create_pipe(dev, endpoint))
#define usb_rcvctrlpipe(dev, endpoint)	((PIPE_CONTROL << 30) | \
					 create_pipe(dev, endpoint) | \
					 USB_DIR_IN)
#define usb_sndisocpipe(dev, endpoint)	((PIPE_ISOCHRONOUS << 30) | \
					 create_pipe(dev, endpoint))
#define usb_rcvisocpipe(dev, endpoint)	((PIPE_ISOCHRONOUS << 30) | \
					 create_pipe(dev, endpoint) | \
					 USB_DIR_IN)
#define usb_sndbulkpipe(dev, endpoint)	((PIPE_BULK << 30) | \
					 create_pipe(dev, endpoint))
#define usb_rcvbulkpipe(dev, endpoint)	((PIPE_BULK << 30) | \
					 create_pipe(dev, endpoint) | \
					 USB_DIR_IN)
#define usb_sndintpipe(dev, endpoint)	((PIPE_INTERRUPT << 30) | \
					 create_pipe(dev, endpoint))
#define usb_rcvintpipe(dev, endpoint)	((PIPE_INTERRUPT << 30) | \
					 create_pipe(dev, endpoint) | \
					 USB_DIR_IN)
#define usb_snddefctrl(dev)		((PIPE_CONTROL << 30) | \
					 default_pipe(dev))
#define usb_rcvdefctrl(dev)		((PIPE_CONTROL << 30) | \
					 default_pipe(dev) | \
					 USB_DIR_IN)

/* The D0/D1 toggle bits */
#define usb_gettoggle(dev, ep, out) (((dev)->toggle[out] >> ep) & 1)
#define usb_dotoggle(dev, ep, out)  ((dev)->toggle[out] ^= (1 << ep))
#define usb_settoggle(dev, ep, out, bit) ((dev)->toggle[out] = \
						((dev)->toggle[out] & \
						 ~(1 << ep)) | ((bit) << ep))

/* Endpoint halt control/status */
#define usb_endpoint_out(ep_dir)	(((ep_dir >> 7) & 1) ^ 1)
#define usb_endpoint_halt(dev, ep, out) ((dev)->halted[out] |= (1 << (ep)))
#define usb_endpoint_running(dev, ep, out) ((dev)->halted[out] &= ~(1 << (ep)))
#define usb_endpoint_halted(dev, ep, out) ((dev)->halted[out] & (1 << (ep)))

#define usb_packetid(pipe)	(((pipe) & USB_DIR_IN) ? USB_PID_IN : \
				 USB_PID_OUT)

#define usb_pipeout(pipe)	((((pipe) >> 7) & 1) ^ 1)
#define usb_pipein(pipe)	(((pipe) >> 7) & 1)
#define usb_pipedevice(pipe)	(((pipe) >> 8) & 0x7f)
#define usb_pipe_endpdev(pipe)	(((pipe) >> 8) & 0x7ff)
#define usb_pipeendpoint(pipe)	(((pipe) >> 15) & 0xf)
#define usb_pipedata(pipe)	(((pipe) >> 19) & 1)
#define usb_pipetype(pipe)	(((pipe) >> 30) & 3)
#define usb_pipeisoc(pipe)	(usb_pipetype((pipe)) == PIPE_ISOCHRONOUS)
#define usb_pipeint(pipe)	(usb_pipetype((pipe)) == PIPE_INTERRUPT)
#define usb_pipecontrol(pipe)	(usb_pipetype((pipe)) == PIPE_CONTROL)
#define usb_pipebulk(pipe)	(usb_pipetype((pipe)) == PIPE_BULK)

#define usb_pipe_ep_index(pipe)	\
		usb_pipecontrol(pipe) ? (usb_pipeendpoint(pipe) * 2) : \
				((usb_pipeendpoint(pipe) * 2) - \
				 (usb_pipein(pipe) ? 0 : 1))

/*************************************************************************
 * Hub Stuff
 */
struct usb_port_status {
	unsigned short wPortStatus;
	unsigned short wPortChange;
} __attribute__ ((packed));

struct usb_hub_status {
	unsigned short wHubStatus;
	unsigned short wHubChange;
} __attribute__ ((packed));


/* Hub descriptor */
struct usb_hub_descriptor {
	unsigned char  bLength;
	unsigned char  bDescriptorType;
	unsigned char  bNbrPorts;
	unsigned short wHubCharacteristics;
	unsigned char  bPwrOn2PwrGood;
	unsigned char  bHubContrCurrent;
	unsigned char  DeviceRemovable[(USB_MAXCHILDREN+1+7)/8];
	unsigned char  PortPowerCtrlMask[(USB_MAXCHILDREN+1+7)/8];
	/* DeviceRemovable and PortPwrCtrlMask want to be variable-length
	   bitmaps that hold max 255 entries. (bit0 is ignored) */
} __attribute__ ((packed));


struct usb_hub_device {
	struct usb_device *pusb_dev;
	struct usb_hub_descriptor desc;
};

int usb_hub_probe(struct usb_device *dev, int ifnum);
void usb_hub_reset(void);
int hub_port_reset(struct usb_device *dev, int port,
			  unsigned short *portstat);

struct usb_device *usb_alloc_new_device(void *controller);

int usb_new_device(struct usb_device *dev);
void usb_free_device(void);
int usb_alloc_device(struct usb_device *dev);
void usb_release_device(struct usb_device *dev);

#endif /*_USB_H_ */
