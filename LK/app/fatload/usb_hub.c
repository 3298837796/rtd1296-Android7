/*
 *
 * Most of this source has been derived from the Linux USB
 * project:
 * (C) Copyright Linus Torvalds 1999
 * (C) Copyright Johannes Erdfelt 1999-2001
 * (C) Copyright Andreas Gal 1999
 * (C) Copyright Gregory P. Smith 1999
 * (C) Copyright Deti Fliegl 1999 (new USB architecture)
 * (C) Copyright Randy Dunlap 2000
 * (C) Copyright David Brownell 2000 (kernel hotplug, usb_device_id)
 * (C) Copyright Yggdrasil Computing, Inc. 2000
 *     (usb_device_id matching changes by Adam J. Richter)
 *
 * Adapted for U-Boot:
 * (C) Copyright 2001 Denis Peter, MPL AG Switzerland
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston,
 * MA 02111-1307 USA
 *
 */

/****************************************************************************
 * HUB "Driver"
 * Probes device for being a hub and configurate it
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <arch/ops.h>
#include <ctype.h>
#include <unaligned.h>
#include <usb.h>

#include <errno.h>

#include <platform.h>

#define CONFIG_SYS_HZ                        1000

#ifndef CONFIG_USB_HUB_MIN_POWER_ON_DELAY
#define CONFIG_USB_HUB_MIN_POWER_ON_DELAY	1000 //hcy  modified orgin :100
#endif

#define USB_BUFSIZ	512

#ifdef CONFIG_USB_UPDATE_WHEN_AC_ON
extern int is_usb_ac_on;
#endif /* CONFIG_USB_UPDATE_WHEN_AC_ON */

static struct usb_hub_device hub_dev[USB_MAX_HUB];
static int usb_hub_index;

#define USB_HUB_PR_SS 3 //hcy  added
static int usb_get_hub_descriptor(struct usb_device *dev, void *data, int size)
{
  	/* hcy added below */
	unsigned short dtype;
	//struct xhci_ctrl *ctrl;
	int is_xhci_roothub = 0;
	int ctrl_type = ((struct controller *) dev->controller)->ctrl_type;
	if (ctrl_type == CTRL_TYPE_XHCI) {
		is_xhci_roothub = (dev->parent == NULL);
		debug("is_xhci_roothub=%d, dev->parent=%p\n",
				is_xhci_roothub, dev->parent);
	}
	if (dev->descriptor.bDeviceProtocol == USB_HUB_PR_SS && is_xhci_roothub != 1)
		dtype = USB_DT_SS_HUB;
	else
		dtype = USB_DT_HUB;

	/* hcy added above */

	return usb_control_msg(dev, usb_rcvctrlpipe(dev, 0),
		USB_REQ_GET_DESCRIPTOR, USB_DIR_IN | USB_RT_HUB,
		dtype /*USB_DT_HUB*/ << 8, 0, data, size, USB_CNTL_TIMEOUT);
}

static int usb_clear_port_feature(struct usb_device *dev, int port, int feature)
{
	return usb_control_msg(dev, usb_sndctrlpipe(dev, 0),
				USB_REQ_CLEAR_FEATURE, USB_RT_PORT, feature,
				port, NULL, 0, USB_CNTL_TIMEOUT);
}

static int usb_set_port_feature(struct usb_device *dev, int port, int feature)
{
	return usb_control_msg(dev, usb_sndctrlpipe(dev, 0),
				USB_REQ_SET_FEATURE, USB_RT_PORT, feature,
				port, NULL, 0, USB_CNTL_TIMEOUT);
}

static int usb_get_hub_status(struct usb_device *dev, void *data)
{
	return usb_control_msg(dev, usb_rcvctrlpipe(dev, 0),
			USB_REQ_GET_STATUS, USB_DIR_IN | USB_RT_HUB, 0, 0,
			data, sizeof(struct usb_hub_status), USB_CNTL_TIMEOUT);
}

static int usb_get_port_status(struct usb_device *dev, int port, void *data)
{
	return usb_control_msg(dev, usb_rcvctrlpipe(dev, 0),
			USB_REQ_GET_STATUS, USB_DIR_IN | USB_RT_PORT, 0, port,
			data, sizeof(struct usb_hub_status), USB_CNTL_TIMEOUT);
}


static void usb_hub_power_on(struct usb_hub_device *hub)
{
	int i;
	struct usb_device *dev;
	unsigned pgood_delay = hub->desc.bPwrOn2PwrGood * 2;
	//ALLOC_CACHE_ALIGN_BUFFER(struct usb_port_status, portsts, 1024);
	unsigned short portstatus;
	int ret;
	struct usb_port_status* portsts;
        portsts = (struct usb_port_status *)memalign(64 ,1024*sizeof(struct usb_port_status));

	dev = hub->pusb_dev;
	/*
	 * Enable power to the ports:
	 * Here we Power-cycle the ports: aka,
	 * turning them off and turning on again.
	 */
	debug("enabling power on all ports\n");
 	for (i = 0; i < dev->maxchild; i++) {
		usb_clear_port_feature(dev, i + 1, USB_PORT_FEAT_POWER);
		debug("port %d returns %lX\n", i + 1, dev->status);
	}
	/* Wait at least 2*bPwrOn2PwrGood for PP to change */
	mdelay(pgood_delay);
	for (i = 0; i < dev->maxchild; i++) {
		ret = usb_get_port_status(dev, i + 1, portsts);
		if (ret < 0) {
			debug("port %d: get_port_status failed\n", i + 1);
			return;
		}

		/*
		 * Check to confirm the state of Port Power:
		 * xHCI says "After modifying PP, s/w shall read
		 * PP and confirm that it has reached the desired state
		 * before modifying it again, undefined behavior may occur
		 * if this procedure is not followed".
		 * EHCI doesn't say anything like this, but no harm in keeping
		 * this.
		 */
		portstatus = le16_to_cpu(portsts->wPortStatus);
		if (portstatus & (USB_PORT_STAT_POWER << 1)) {
			debug("port %d: Port power change failed\n", i + 1);
			return;
		}

	}
	for (i = 0; i < dev->maxchild; i++) {
		usb_set_port_feature(dev, i + 1, USB_PORT_FEAT_POWER);
		debug("port %d returns %lX\n", i + 1, dev->status);
	}

	/* Wait for power to become stable */
	mdelay(max(pgood_delay, (unsigned int)CONFIG_USB_HUB_MIN_POWER_ON_DELAY));
}

void usb_hub_reset(void)
{
	usb_hub_index = 0;
}

static struct usb_hub_device *usb_hub_allocate(void)
{
	if (usb_hub_index < USB_MAX_HUB)
		return &hub_dev[usb_hub_index++];

	printf("ERROR: USB_MAX_HUB (%d) reached\n", USB_MAX_HUB);
	return NULL;
}

#define MAX_TRIES 5

static inline char *portspeed(int portstatus)
{
	char *speed_str;
	char speed_su[]="5 Gb/s";
	char speed_hi[]="480 Mb/s";
	char speed_lo[]="1.5 Mb/s";
	char speed_fu[]="12 Mb/s";


	switch (portstatus & USB_PORT_STAT_SPEED_MASK) {
	case USB_PORT_STAT_SUPER_SPEED:
		speed_str = speed_su;
		break;
	case USB_PORT_STAT_HIGH_SPEED:
		speed_str = speed_hi;
		break;
	case USB_PORT_STAT_LOW_SPEED:
		speed_str = speed_lo;
		break;
	default:
		speed_str = speed_fu;
		break;
	}

	return speed_str;
}
int hub_port_reset(struct usb_device *dev, int port,
			unsigned short *portstat)
{
	int tries;
	//ALLOC_CACHE_ALIGN_BUFFER(struct usb_port_status, portsts, 1024);
	unsigned short portstatus, portchange;
	struct usb_port_status *portsts;
        portsts = (struct usb_port_status *)memalign(64 ,1024*sizeof(struct usb_port_status));


	debug("hub_port_reset: resetting port %d...\n", port);
	for (tries = 0; tries < MAX_TRIES; tries++) {

		usb_set_port_feature(dev, port + 1, USB_PORT_FEAT_RESET);
		mdelay(200);

		if (usb_get_port_status(dev, port + 1, portsts) < 0) {
			debug("get_port_status failed status %lX\n",
			      dev->status);
			return -1;
		}
		portstatus = le16_to_cpu(portsts->wPortStatus);
		portchange = le16_to_cpu(portsts->wPortChange);
		debug("dev->descriptor.bDeviceProtocol = 0x%x \n", dev->descriptor.bDeviceProtocol);//hcy test
	//	if (dev->descriptor.bDeviceProtocol == USB_HUB_PR_SS) //hcy test
	//		debug("portstatus %x, change %x, 5 Gb/s\n", portstatus, portchange
	//						);
	//
	//	else
			debug("portstatus %x, change %x, %s\n", portstatus, portchange,
							portspeed(portstatus));

		debug("STAT_C_CONNECTION = %d STAT_CONNECTION = %d" \
		      "  USB_PORT_STAT_ENABLE %d\n",
		      (portchange & USB_PORT_STAT_C_CONNECTION) ? 1 : 0,
		      (portstatus & USB_PORT_STAT_CONNECTION) ? 1 : 0,
		      (portstatus & USB_PORT_STAT_ENABLE) ? 1 : 0);

		if ((portchange & USB_PORT_STAT_C_CONNECTION) ||
		    !(portstatus & USB_PORT_STAT_CONNECTION))
			return -1;

		if (portstatus & USB_PORT_STAT_ENABLE)
			break;

		mdelay(200);
	}

	if (tries == MAX_TRIES) {
		debug("Cannot enable port %i after %i retries, " \
		      "disabling port.\n", port + 1, MAX_TRIES);
		debug("Maybe the USB cable is bad?\n");
		return -1;
	}

	usb_clear_port_feature(dev, port + 1, USB_PORT_FEAT_C_RESET);
#if 0  //hcy test    
	/* hcy test below , just watch the status*/
	mdelay(200); 
	if (usb_get_port_status(dev, port + 1, portsts) < 0) {
			debug("get_port_status failed status %lX\n",
			      dev->status);
			return -1;
		}
	/* hcy test above */
#endif
	*portstat = portstatus;
	return 0;
}


void usb_hub_port_connect_change(struct usb_device *dev, int port)
{
	struct usb_device *usb;
	//ALLOC_CACHE_ALIGN_BUFFER(struct usb_port_status, portsts, 1024);
	unsigned short portstatus;
	struct usb_port_status *portsts;
        portsts = (struct usb_port_status *)memalign(64 ,1024*sizeof(struct usb_port_status));


	/* Check status */
	if (usb_get_port_status(dev, port + 1, portsts) < 0) {
		debug("get_port_status failed\n");
		return;
	}

	portstatus = le16_to_cpu(portsts->wPortStatus);
	debug("portstatus %x, change %x, %s\n",
	      portstatus,
	      le16_to_cpu(portsts->wPortChange),
	      portspeed(portstatus));

	/* Clear the connection change status */
	usb_clear_port_feature(dev, port + 1, USB_PORT_FEAT_C_CONNECTION);

	/* Disconnect any existing devices under this port */
	if (((!(portstatus & USB_PORT_STAT_CONNECTION)) &&
	     (!(portstatus & USB_PORT_STAT_ENABLE))) || (dev->children[port])) {
		debug("usb_disconnect(&hub->children[port]);\n");
		/* Return now if nothing is connected */
		if (!(portstatus & USB_PORT_STAT_CONNECTION))
			return;
	}
	mdelay(200);
	/* Reset the port */
	if (hub_port_reset(dev, port, &portstatus) < 0) {
		printf("cannot reset port %i!?\n", port + 1);
		return;
	}
#if 0
	if (portstatus & 0x100) {
		printf("\n#@# %s(%d) skip full speed device in port %d!\n",
			__func__, __LINE__, port + 1);
		return;
	}
#endif

	mdelay(200);
	/* hcy added below */

	usb_clear_port_feature(dev, port + 1, USB_PORT_FEAT_C_CONNECTION);
	/* hcy added above */
	/* Allocate a new device struct for it */
	usb = usb_alloc_new_device(dev->controller);

	switch (portstatus & USB_PORT_STAT_SPEED_MASK) {
	case USB_PORT_STAT_SUPER_SPEED:
		usb->speed = USB_SPEED_SUPER;
		debug("USB_SPEED_SUPER\n");//hcy added
		break;
	case USB_PORT_STAT_HIGH_SPEED:
		usb->speed = USB_SPEED_HIGH;
		debug("USB_SPEED_HIGH\n");//hcy added
		break;
	case USB_PORT_STAT_LOW_SPEED:
		if (dev->descriptor.bDeviceProtocol == USB_HUB_PR_SS)
		{		 //hcy test added
			usb->speed = USB_SPEED_SUPER;
			/* hcy added below */
			usb_clear_port_feature(dev, port + 1, USB_SS_PORT_FEAT_C_BH_RESET );
			usb_clear_port_feature(dev, port + 1, USB_SS_PORT_FEAT_C_LINK_STATE);
			/* hcy added above */

			debug("USB_SPEED_SUPER\n");//hcy added

		}
		else
  			{
			usb->speed = USB_SPEED_LOW;
			debug("USB_SPEED_LOW\n");//hcy added
		}
		break;
	default:
		usb->speed = USB_SPEED_FULL;
		debug("USB_SPEED_FULL\n");//hcy added
		break;
	}



	dev->children[port] = usb;
	usb->parent = dev;
	usb->portnr = port + 1;
	/* hcy adde below */
	usb->level = dev->level + 1;
	debug("dev->level = 0x%x \n", dev->level);
	debug("usb->level = 0x%x \n", usb->level);
	debug("port = 0x%x \n", port); 
	debug("dev->route =0x%x \n", dev->route); 
	debug("((port+1) << (( dev->level-1)*4)=0x%x \n", (port+1) << (( dev->level-1)*4));
	if (dev->level != 0){ 
		if (port < 15)
			usb->route = dev->route + ((port+1) << (( dev->level-1)*4));  
		else
			usb->route = dev->route + (15 << (( dev->level-1)*4));
	}
	debug("usb->route =0x%x \n", usb->route); 
	/* hcy added above */
	
	/* Run it through the hoops (find a driver, etc) */
	if (usb_new_device(usb)) {
		/* Woops, disable the port */
		usb_free_device();
		dev->children[port] = NULL;
		debug("hub: disabling port %d\n", port + 1);
		usb_clear_port_feature(dev, port + 1, USB_PORT_FEAT_ENABLE);
	}
}


static int usb_hub_configure(struct usb_device *dev)
{
	int i;
	//ALLOC_CACHE_ALIGN_BUFFER(unsigned char, buffer, 1024);
	unsigned char *bitmap;
	short hubCharacteristics;
	struct usb_hub_descriptor *descriptor;
	struct usb_hub_device *hub;
#ifdef DEBUG
	/*__maybe_unused*/ struct usb_hub_status *hubsts;
#endif 
#ifdef CONFIG_USB_UPDATE_WHEN_AC_ON
	int portchange_count = 0;
#endif /* CONFIG_USB_UPDATE_WHEN_AC_ON */
	unsigned char *buffer;
        buffer = (unsigned char *)memalign(64 ,1024*sizeof(unsigned char));


	dev->route = 0;   /* hcy added */
	/* "allocate" Hub device */
	hub = usb_hub_allocate();
	if (hub == NULL)
		return -1;
	hub->pusb_dev = dev;
	/* Get the the hub descriptor */
	if (usb_get_hub_descriptor(dev, buffer, 12 /*4*/) < 0) {
		debug("usb_hub_configure: failed to get hub " \
		      "descriptor, giving up %lX\n", dev->status);
		return -1;
	}
	descriptor = (struct usb_hub_descriptor *)buffer;

	/* silence compiler warning if USB_BUFSIZ is > 256 [= sizeof(char)] */
	i = descriptor->bLength;
	if (i > USB_BUFSIZ) {
		debug("usb_hub_configure: failed to get hub " \
		      "descriptor - too long: %d\n", descriptor->bLength);
		return -1;
	}

	if (usb_get_hub_descriptor(dev, buffer, descriptor->bLength) < 0) {
		debug("usb_hub_configure: failed to get hub " \
		      "descriptor 2nd giving up %lX\n", dev->status);
		return -1;
	}
	memcpy((unsigned char *)&hub->desc, buffer, descriptor->bLength);
	/* adjust 16bit values */
	put_unaligned(le16_to_cpu(get_unaligned(
			&descriptor->wHubCharacteristics)),
			&hub->desc.wHubCharacteristics);
	/* set the bitmap */
	bitmap = (unsigned char *)&hub->desc.DeviceRemovable[0];
	/* devices not removable by default */
	memset(bitmap, 0xff, (USB_MAXCHILDREN+1+7)/8);
	bitmap = (unsigned char *)&hub->desc.PortPowerCtrlMask[0];
	memset(bitmap, 0xff, (USB_MAXCHILDREN+1+7)/8); /* PowerMask = 1B */

	for (i = 0; i < ((hub->desc.bNbrPorts + 1 + 7)/8); i++)
		hub->desc.DeviceRemovable[i] = descriptor->DeviceRemovable[i];

	for (i = 0; i < ((hub->desc.bNbrPorts + 1 + 7)/8); i++)
		hub->desc.PortPowerCtrlMask[i] = descriptor->PortPowerCtrlMask[i];

	dev->maxchild = descriptor->bNbrPorts;
	debug("%d ports detected\n", dev->maxchild);

	hubCharacteristics = get_unaligned(&hub->desc.wHubCharacteristics);
	switch (hubCharacteristics & HUB_CHAR_LPSM) {
	case 0x00:
		debug("ganged power switching\n");
		break;
	case 0x01:
		debug("individual port power switching\n");
		break;
	case 0x02:
	case 0x03:
		debug("unknown reserved power switching mode\n");
		break;
	}

	if (hubCharacteristics & HUB_CHAR_COMPOUND)
		debug("part of a compound device\n");
	else
		debug("standalone hub\n");

	switch (hubCharacteristics & HUB_CHAR_OCPM) {
	case 0x00:
		debug("global over-current protection\n");
		break;
	case 0x08:
		debug("individual port over-current protection\n");
		break;
	case 0x10:
	case 0x18:
		debug("no over-current protection\n");
		break;
	}

	debug("power on to power good time: %dms\n",
	      descriptor->bPwrOn2PwrGood * 2);
	debug("hub controller current requirement: %dmA\n",
	      descriptor->bHubContrCurrent);

	for (i = 0; i < dev->maxchild; i++)
		debug("port %d is%s removable\n", i + 1,
		      hub->desc.DeviceRemovable[(i + 1) / 8] & \
		      (1 << ((i + 1) % 8)) ? " not" : "");

	if (sizeof(struct usb_hub_status) > USB_BUFSIZ) {
		debug("usb_hub_configure: failed to get Status - " \
		      "too long: %d\n", descriptor->bLength);
		return -1;
	}

	if (usb_get_hub_status(dev, buffer) < 0) {
		debug("usb_hub_configure: failed to get Status %lX\n",
		      dev->status);
		return -1;
	}

#ifdef DEBUG
	hubsts = (struct usb_hub_status *)buffer;


	debug("get_hub_status returned status %X, change %X\n",
	      le16_to_cpu(hubsts->wHubStatus),
	      le16_to_cpu(hubsts->wHubChange));
	debug("local power source is %s\n",
	      (le16_to_cpu(hubsts->wHubStatus) & HUB_STATUS_LOCAL_POWER) ? \
	      "lost (inactive)" : "good");
	debug("%sover-current condition exists\n",
	      (le16_to_cpu(hubsts->wHubStatus) & HUB_STATUS_OVERCURRENT) ? \
	      "" : "no ");
#endif
	/* HUB_SET_DEPTH hcy added */
	if (dev->speed == USB_SPEED_SUPER)  
		usb_control_msg(dev, usb_sndctrlpipe(dev, 0),
        	                      HUB_SET_DEPTH , USB_RT_HUB, 0,    
                	                0, NULL, 0, USB_CNTL_TIMEOUT);

	usb_hub_power_on(hub);


	for (i = 0; i < dev->maxchild; i++) {
		//ALLOC_CACHE_ALIGN_BUFFER(struct usb_port_status, portsts, 1024);
		unsigned short portstatus, portchange;
		int ret;
		ulong start = current_time();//get_timer(0);
		struct usb_port_status *portsts;
        	portsts = (struct usb_port_status *)memalign(64 ,1024*sizeof(struct usb_port_status));


		/*
		 * Wait for (whichever finishes first)
		 *  - A maximum of 10 seconds
		 *    This is a purely observational value driven by connecting
		 *    a few broken pen drives and taking the max * 1.5 approach
		 *  - connection_change and connection state to report same
		 *    state
		 */
		do {
			ret = usb_get_port_status(dev, i + 1, portsts);
			if (ret < 0) {
				debug("get_port_status failed\n");
				break;
			}

			portstatus = le16_to_cpu(portsts->wPortStatus);
			portchange = le16_to_cpu(portsts->wPortChange);

			if ((portchange & USB_PORT_STAT_C_CONNECTION) ==
				(portstatus & USB_PORT_STAT_CONNECTION))
				break;

		} while ((current_time() - start) < CONFIG_SYS_HZ * 10);

		if (ret < 0)
			continue;

		debug("Port %d Status %X Change %X\n",
		      i + 1, portstatus, portchange);

#ifdef CONFIG_USB_UPDATE_WHEN_AC_ON
		if (is_usb_ac_on && (portchange & USB_PORT_STAT_C_CONNECTION)) {
			portchange_count++;
			printf("\n#@# %s(%d) %d portchange_count %d\n",
					__func__, __LINE__, i + 1, portchange_count);
			if (portchange_count > 1)
				continue;
		}
#endif /* CONFIG_USB_UPDATE_WHEN_AC_ON */

		// add by cfyeh, skip usb3 port 
		if ((i + 1) == 0x5) {
			printf("\n#@# %s(%d) skip the device in usb3 port %d!\n",
				__func__, __LINE__, i + 1);
			continue;
		}

		// add by cfyeh, skip low speed
		if (portstatus == 0x301) {
			printf("\n#@# %s(%d) skip low speed device in port %d!\n",
				__func__, __LINE__, i + 1);
			continue;
		}

		if (portchange & USB_PORT_STAT_C_CONNECTION) {
			debug("port %d connection change\n", i + 1);

			usb_hub_port_connect_change(dev, i);
		}
		if (portchange & USB_PORT_STAT_C_ENABLE) {
			debug("port %d enable change, status %x\n",
			      i + 1, portstatus);
			usb_clear_port_feature(dev, i + 1,
						USB_PORT_FEAT_C_ENABLE);
			/*
			 * The following hack causes a ghost device problem
			 * to Faraday EHCI
			 */
#ifndef CONFIG_USB_EHCI_FARADAY
			/* EM interference sometimes causes bad shielded USB
			 * devices to be shutdown by the hub, this hack enables
			 * them again. Works at least with mouse driver */
			if (!(portstatus & USB_PORT_STAT_ENABLE) &&
			     (portstatus & USB_PORT_STAT_CONNECTION) &&
			     ((dev->children[i]))) {
				debug("already running port %i "  \
				      "disabled by hub (EMI?), " \
				      "re-enabling...\n", i + 1);
				      usb_hub_port_connect_change(dev, i);
			}
#endif
		}
		if (portstatus & USB_PORT_STAT_SUSPEND) {
			debug("port %d suspend change\n", i + 1);
			usb_clear_port_feature(dev, i + 1,
						USB_PORT_FEAT_SUSPEND);
		}

		if (portchange & USB_PORT_STAT_C_OVERCURRENT) {
			debug("port %d over-current change\n", i + 1);
			usb_clear_port_feature(dev, i + 1,
						USB_PORT_FEAT_C_OVER_CURRENT);
			usb_hub_power_on(hub);
		}

		if (portchange & USB_PORT_STAT_C_RESET) {
			debug("port %d reset change\n", i + 1);
			usb_clear_port_feature(dev, i + 1,
						USB_PORT_FEAT_C_RESET);
		}
	} /* end for i all ports */

#ifdef CONFIG_USB_UPDATE_WHEN_AC_ON
	if (is_usb_ac_on && (portchange_count > 1)) {
		puts("USB error: just support one device in CONFIG_USB_UPDATE_WHEN_AC_ON mode\n");
		return -1;
	}
#endif /* CONFIG_USB_UPDATE_WHEN_AC_ON */

	return 0;
}

int usb_hub_probe(struct usb_device *dev, int ifnum)
{
	struct usb_interface *iface;
	struct usb_endpoint_descriptor *ep;
	int ret;

	iface = &dev->config.if_desc[ifnum];
	/* Is it a hub? */
	if (iface->desc.bInterfaceClass != USB_CLASS_HUB)
		return 0;
	/* Some hubs have a subclass of 1, which AFAICT according to the */
	/*  specs is not defined, but it works */
	if ((iface->desc.bInterfaceSubClass != 0) &&
	    (iface->desc.bInterfaceSubClass != 1))
		return 0;
	/* Multiple endpoints? What kind of mutant ninja-hub is this? */
	if (iface->desc.bNumEndpoints != 1)
		return 0;
	ep = &iface->ep_desc[0];
	/* Output endpoint? Curiousier and curiousier.. */
	if (!(ep->bEndpointAddress & USB_DIR_IN))
		return 0;
	/* If it's not an interrupt endpoint, we'd better punt! */
	if ((ep->bmAttributes & 3) != 3)
		return 0;
	/* We found a hub */
	debug("USB hub found\n");
	ret = usb_hub_configure(dev);
	return ret;
}
