/*
 * (C) Copyright 2001
 * Denis Peter, MPL AG Switzerland
 *
 * Most of this source has been derived from the Linux USB
 * project.
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
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <part.h>
#include <usb.h>

#include <lib/console.h>

#define simple_strtoul strtoul

#ifdef CONFIG_USB_STORAGE
static int usb_stor_curr_dev = -1; /* current device */
#endif
#ifdef CONFIG_USB_HOST_ETHER
static int usb_ether_curr_dev = -1; /* current ethernet device */
#endif

/* some display routines (info command) */
static const char *usb_get_class_desc(unsigned char dclass)
{
	static const char uc1[] = "See Interface";
	static const char uc2[] = "Audio";
	static const char uc3[] = "Communication";
	static const char uc4[] = "Human Interface";

	static const char uc5[] ="Printer";

	static const char uc6[] = "Mass Storage";

	static const char uc7[] = "Hub";

	static const char uc8[] = "CDC Data";

	static const char uc9[] = "Vendor specific";
	static const char uc10[] = "Null";
	const char *uc;


	switch (dclass) {
	case USB_CLASS_PER_INTERFACE:
		uc = uc1; //"See Interface";
		break;
	case USB_CLASS_AUDIO:
		uc = uc2; //"Audio";
		break;
	case USB_CLASS_COMM:
		uc = uc3; //"Communication";
		break;
	case USB_CLASS_HID:
		uc = uc4; //"Human Interface";
		break;
	case USB_CLASS_PRINTER:
		uc = uc5; //"Printer";
		break;
	case USB_CLASS_MASS_STORAGE:
		uc = uc6; //"Mass Storage";
		break;
	case USB_CLASS_HUB:
		uc = uc7; //"Hub";
		break;
	case USB_CLASS_DATA:
		uc = uc8; //"CDC Data";
		break;
	case USB_CLASS_VENDOR_SPEC:
		uc = uc9; //"Vendor specific";
		break;
	default:
		uc = uc10; //"";
	}

	return uc ;
}

static void usb_display_class_sub(unsigned char dclass, unsigned char subclass,
				  unsigned char proto)
{
	switch (dclass) {
	case USB_CLASS_PER_INTERFACE:
		printf("See Interface");
		break;
	case USB_CLASS_HID:
		printf("Human Interface, Subclass: ");
		switch (subclass) {
		case USB_SUB_HID_NONE:
			printf("None");
			break;
		case USB_SUB_HID_BOOT:
			printf("Boot ");
			switch (proto) {
			case USB_PROT_HID_NONE:
				printf("None");
				break;
			case USB_PROT_HID_KEYBOARD:
				printf("Keyboard");
				break;
			case USB_PROT_HID_MOUSE:
				printf("Mouse");
				break;
			default:
				printf("reserved");
				break;
			}
			break;
		default:
			printf("reserved");
			break;
		}
		break;
	case USB_CLASS_MASS_STORAGE:
		printf("Mass Storage, ");
		switch (subclass) {
		case US_SC_RBC:
			printf("RBC ");
			break;
		case US_SC_8020:
			printf("SFF-8020i (ATAPI)");
			break;
		case US_SC_QIC:
			printf("QIC-157 (Tape)");
			break;
		case US_SC_UFI:
			printf("UFI");
			break;
		case US_SC_8070:
			printf("SFF-8070");
			break;
		case US_SC_SCSI:
			printf("Transp. SCSI");
			break;
		default:
			printf("reserved");
			break;
		}
		printf(", ");
		switch (proto) {
		case US_PR_CB:
			printf("Command/Bulk");
			break;
		case US_PR_CBI:
			printf("Command/Bulk/Int");
			break;
		case US_PR_BULK:
			printf("Bulk only");
			break;
		default:
			printf("reserved");
			break;
		}
		break;
	default:
		printf("%s", usb_get_class_desc(dclass));
		break;
	}
}

static void usb_display_string(struct usb_device *dev, int index)
{
	//ALLOC_CACHE_ALIGN_BUFFER(char, buffer, 1024);
	char *buffer;
	buffer = (char*)memalign(64 ,1024);
	if (index != 0) {
		if (usb_string(dev, index, &buffer[0], 256) > 0)
			printf("String: \"%s\"", buffer);
	}
}

static void usb_display_desc(struct usb_device *dev)
{
	if (dev->descriptor.bDescriptorType == USB_DT_DEVICE) {
		printf("%d: %s, USB Revision %x.%x\n", dev->devnum,
		usb_get_class_desc(dev->config.if_desc[0].desc.bInterfaceClass),
				   (dev->descriptor.bcdUSB>>8) & 0xff,
				   dev->descriptor.bcdUSB & 0xff);

		if (strlen(dev->mf) || strlen(dev->prod) ||
		    strlen(dev->serial))
			printf(" - %s %s %s\n", dev->mf, dev->prod,
				dev->serial);
		if (dev->descriptor.bDeviceClass) {
			printf(" - Class: ");
			usb_display_class_sub(dev->descriptor.bDeviceClass,
					      dev->descriptor.bDeviceSubClass,
					      dev->descriptor.bDeviceProtocol);
			printf("\n");
		} else {
			printf(" - Class: (from Interface) %s\n",
			       usb_get_class_desc(
				dev->config.if_desc[0].desc.bInterfaceClass));
		}
		printf(" - PacketSize: %d  Configurations: %d\n",
			dev->descriptor.bMaxPacketSize0,
			dev->descriptor.bNumConfigurations);
		printf(" - Vendor: 0x%04x  Product 0x%04x Version %d.%d\n",
			dev->descriptor.idVendor, dev->descriptor.idProduct,
			(dev->descriptor.bcdDevice>>8) & 0xff,
			dev->descriptor.bcdDevice & 0xff);
	}

}

static void usb_display_conf_desc(struct usb_config_descriptor *config,
				  struct usb_device *dev)
{
	printf("   Configuration: %d\n", config->bConfigurationValue);
	printf("   - Interfaces: %d %s%s%dmA\n", config->bNumInterfaces,
	       (config->bmAttributes & 0x40) ? "Self Powered " : "Bus Powered ",
	       (config->bmAttributes & 0x20) ? "Remote Wakeup " : "",
		config->bMaxPower*2);
	if (config->iConfiguration) {
		printf("   - ");
		usb_display_string(dev, config->iConfiguration);
		printf("\n");
	}
}

static void usb_display_if_desc(struct usb_interface_descriptor *ifdesc,
				struct usb_device *dev)
{
	printf("     Interface: %d\n", ifdesc->bInterfaceNumber);
	printf("     - Alternate Setting %d, Endpoints: %d\n",
		ifdesc->bAlternateSetting, ifdesc->bNumEndpoints);
	printf("     - Class ");
	usb_display_class_sub(ifdesc->bInterfaceClass,
		ifdesc->bInterfaceSubClass, ifdesc->bInterfaceProtocol);
	printf("\n");
	if (ifdesc->iInterface) {
		printf("     - ");
		usb_display_string(dev, ifdesc->iInterface);
		printf("\n");
	}
}

static void usb_display_ep_desc(struct usb_endpoint_descriptor *epdesc)
{
	printf("     - Endpoint %d %s ", epdesc->bEndpointAddress & 0xf,
		(epdesc->bEndpointAddress & 0x80) ? "In" : "Out");
	switch ((epdesc->bmAttributes & 0x03)) {
	case 0:
		printf("Control");
		break;
	case 1:
		printf("Isochronous");
		break;
	case 2:
		printf("Bulk");
		break;
	case 3:
		printf("Interrupt");
		break;
	}
	//printf(" MaxPacket %d", get_unaligned(&epdesc->wMaxPacketSize));
	if ((epdesc->bmAttributes & 0x03) == 0x3)
		printf(" Interval %dms", epdesc->bInterval);
	printf("\n");
}

/* main routine to diasplay the configs, interfaces and endpoints */
static void usb_display_config(struct usb_device *dev)
{
	struct usb_config *config;
	struct usb_interface *ifdesc;
	struct usb_endpoint_descriptor *epdesc;
	int i, ii;

	config = &dev->config;
	usb_display_conf_desc(&config->desc, dev);
	for (i = 0; i < config->no_of_if; i++) {
		ifdesc = &config->if_desc[i];
		usb_display_if_desc(&ifdesc->desc, dev);
		for (ii = 0; ii < ifdesc->no_of_ep; ii++) {
			epdesc = &ifdesc->ep_desc[ii];
			usb_display_ep_desc(epdesc);
		}
	}
	printf("\n");
}

static struct usb_device *usb_find_device(int devnum)
{
	struct usb_device *dev;
	int d;

	for (d = 0; d < USB_MAX_DEVICE; d++) {
		dev = usb_get_dev_index(d);
		if (dev == NULL)
			return NULL;
		if (dev->devnum == devnum)
			return dev;
	}

	return NULL;
}

static inline const char *portspeed(int speed)
{
	const char *speed_str;

	switch (speed) {
	case USB_SPEED_SUPER:
		speed_str = "5 Gb/s";
		break;
	case USB_SPEED_HIGH:
		speed_str = "480 Mb/s";
		break;
	case USB_SPEED_LOW:
		speed_str = "1.5 Mb/s";
		break;
	default:
		speed_str = "12 Mb/s";
		break;
	}

	return speed_str;
}

/* shows the device tree recursively */
static void usb_show_tree_graph(struct usb_device *dev, char *pre)
{
	int i, index;
	int has_child, last_child;

	index = strlen(pre);
	printf(" %s", pre);
	/* check if the device has connected children */
	has_child = 0;
	for (i = 0; i < dev->maxchild; i++) {
		if (dev->children[i] != NULL)
			has_child = 1;
	}
	/* check if we are the last one */
	last_child = 1;
	if (dev->parent != NULL) {
		for (i = 0; i < dev->parent->maxchild; i++) {
			/* search for children */
			if (dev->parent->children[i] == dev) {
				/* found our pointer, see if we have a
				 * little sister
				 */
				while (i++ < dev->parent->maxchild) {
					if (dev->parent->children[i] != NULL) {
						/* found a sister */
						last_child = 0;
						break;
					} /* if */
				} /* while */
			} /* device found */
		} /* for all children of the parent */
		printf("\b+-");
		/* correct last child */
		if (last_child)
			pre[index-1] = ' ';
	} /* if not root hub */
	else
		printf(" ");
	printf("%d ", dev->devnum);
	pre[index++] = ' ';
	pre[index++] = has_child ? '|' : ' ';
	pre[index] = 0;
	printf(" %s (%s, %dmA)\n", usb_get_class_desc(
					dev->config.if_desc[0].desc.bInterfaceClass),
					portspeed(dev->speed),
					dev->config.desc.bMaxPower * 2);
	if (strlen(dev->mf) || strlen(dev->prod) || strlen(dev->serial))
		printf(" %s  %s %s %s\n", pre, dev->mf, dev->prod, dev->serial);
	printf(" %s\n", pre);
	if (dev->maxchild > 0) {
		for (i = 0; i < dev->maxchild; i++) {
			if (dev->children[i] != NULL) {
				usb_show_tree_graph(dev->children[i], pre);
				pre[index] = 0;
			}
		}
	}
}

/* main routine for the tree command */
static void usb_show_tree(struct usb_device *dev)
{
	char preamble[32];

	memset(preamble, 0, 32);
	usb_show_tree_graph(dev, &preamble[0]);
}

static int usb_test(struct usb_device *dev, int port, char* arg)
{
	int mode;

	if (port > dev->maxchild) {
		printf("Device is no hub or does not have %d ports.\n", port);
		return 1;
	}

	switch (arg[0]) {
	case 'J':
	case 'j':
		printf("Setting Test_J mode");
		mode = USB_TEST_MODE_J;
		break;
	case 'K':
	case 'k':
		printf("Setting Test_K mode");
		mode = USB_TEST_MODE_K;
		break;
	case 'S':
	case 's':
		printf("Setting Test_SE0_NAK mode");
		mode = USB_TEST_MODE_SE0_NAK;
		break;
	case 'P':
	case 'p':
		printf("Setting Test_Packet mode");
		mode = USB_TEST_MODE_PACKET;
		break;
	case 'F':
	case 'f':
		printf("Setting Test_Force_Enable mode");
		mode = USB_TEST_MODE_FORCE_ENABLE;
		break;
	default:
		printf("Unrecognized test mode: %s\nAvailable modes: "
		       "J, K, S[E0_NAK], P[acket], F[orce_Enable]\n", arg);
		return 1;
	}

	if (port)
		printf(" on downstream facing port %d...\n", port);
	else
		printf(" on upstream facing port...\n");

	if (usb_control_msg(dev, usb_sndctrlpipe(dev, 0), USB_REQ_SET_FEATURE,
			    port ? USB_RT_PORT : USB_RECIP_DEVICE,
			    port ? USB_PORT_FEAT_TEST : USB_FEAT_TEST,
			    (mode << 8) | port,
			    NULL, 0, USB_CNTL_TIMEOUT) == -1) {
		printf("Error during SET_FEATURE.\n");
		return 1;
	} else {
		printf("Test mode successfully set. Use 'usb start' "
		       "to return to normal operation.\n");
		return 0;
	}
}


/******************************************************************************
 * usb boot command intepreter. Derived from diskboot
 */
#ifdef CONFIG_USB_STORAGE
#ifdef CONFIG_CMD_USBBOOT
static int do_usbboot(cmd_tbl_t *cmdtp, int flag, int argc, char * const argv[])
{
	return common_diskboot(cmdtp, "usb", argc, argv);
}
#endif /* CONFIG_CMD_USBBOOT */
#endif /* CONFIG_USB_STORAGE */

#ifdef CONFIG_USB_UPDATE_WHEN_AC_ON
int is_usb_ac_on = 0;
#endif /* CONFIG_USB_UPDATE_WHEN_AC_ON */

/******************************************************************************
 * usb command intepreter
 */
static int do_usb(int argc, const cmd_args *argv)
{

	int i;
	struct usb_device *dev = NULL;
	extern char usb_started;
#ifdef CONFIG_USB_STORAGE
	block_dev_desc_t *stor_dev;
#endif

	if (argc < 2)
		return -1;

	if ((strncmp(argv[1].str, "reset", 5) == 0) ||
		 (strncmp(argv[1].str, "start", 5) == 0) ||
		 (strncmp(argv[1].str, "start_ac", 8) == 0)) {
#ifdef CONFIG_USB_UPDATE_WHEN_AC_ON
		if (strncmp(argv[1].str, "start_ac", 8) == 0)
			is_usb_ac_on = 1;
		else
			is_usb_ac_on = 0;
	printf("#@# %s(%d) is_usb_ac_on %d\n", __func__, __LINE__, is_usb_ac_on);
#endif /* CONFIG_USB_UPDATE_WHEN_AC_ON */
		usb_stop();
		printf("(Re)start USB...\n");
		if (usb_init() >= 0) {
#ifdef CONFIG_USB_RTKBT
			/* try to recognize bt devices immediately */
			usb_bt_scan(1);
#endif
#ifdef CONFIG_USB_STORAGE
			/* try to recognize storage devices immediately */
			usb_stor_curr_dev = usb_stor_scan(1);
#endif
#ifdef CONFIG_USB_HOST_ETHER
			/* try to recognize ethernet devices immediately */
			usb_ether_curr_dev = usb_host_eth_scan(1);
#endif
#ifdef CONFIG_USB_KEYBOARD
			drv_usb_kbd_init();
#endif
		}
		{
			int d;

			for (d = 0; d < USB_MAX_DEVICE; d++) {
				dev = usb_get_dev_index(d);
				if (dev == NULL)
					break;
				usb_display_desc(dev);
				usb_display_config(dev);
			}
			return 0;
		}
		return 0;
	}
	if (strncmp(argv[1].str, "stop", 4) == 0) {
#ifdef CONFIG_USB_KEYBOARD
		if (argc == 2) {
			if (usb_kbd_deregister() != 0) {
				printf("USB not stopped: usbkbd still"
					" using USB\n");
				return 1;
			}
		} else {
			/* forced stop, switch console in to serial */
			console_assign(stdin, "serial");
			usb_kbd_deregister();
		}
#endif
		printf("stopping USB..\n");
		usb_stop();
		return 0;
	}
	if (!usb_started) {
		printf("USB is stopped. Please issue 'usb start' first.\n");
		return 1;
	}
	if (strncmp(argv[1].str, "tree", 4) == 0) {
		puts("USB device tree:\n");
		for (i = 0; i < USB_MAX_DEVICE; i++) {
			dev = usb_get_dev_index(i);
			if (dev == NULL)
				break;
			if (dev->parent == NULL)
				usb_show_tree(dev);
		}
		return 0;
	}
	if (strncmp(argv[1].str, "inf", 3) == 0) {
		int d;
		if (argc == 2) {
			for (d = 0; d < USB_MAX_DEVICE; d++) {
				dev = usb_get_dev_index(d);
				if (dev == NULL)
					break;
				usb_display_desc(dev);
				usb_display_config(dev);
			}
			return 0;
		} else {
			i = simple_strtoul(argv[2].str, NULL, 10);
			printf("config for device %d\n", i);
			dev = usb_find_device(i);
			if (dev == NULL) {
				printf("*** No device available ***\n");
				return 0;
			} else {
				usb_display_desc(dev);
				usb_display_config(dev);
			}
		}
		return 0;
	}
#if 0
	if (strncmp(argv[1].str, "test", 4) == 0) {
		if (argc < 5)
			return -1;
		i = simple_strtoul(argv[2].str, NULL, 10);
		dev = usb_find_device(i);
		if (dev == NULL) {
			printf("Device %d does not exist.\n", i);
			return 1;
		}
		i = simple_strtoul(argv[3].str, NULL, 10);
		return usb_test(dev, i, argv[4]);
	}
#endif
#ifdef CONFIG_USB_STORAGE
	if (strncmp(argv[1].str, "stor", 4) == 0)
		return usb_stor_info();

	if (strncmp(argv[1].str, "part", 4) == 0) {
		int devno, ok = 0;
		if (argc == 2) {
			for (devno = 0; ; ++devno) {
				stor_dev = usb_stor_get_dev(devno);
				if (stor_dev == NULL)
					break;
				if (stor_dev->type != DEV_TYPE_UNKNOWN) {
					ok++;
					if (devno)
						printf("\n");
					debug("print_part of %x\n", devno);
					print_part(stor_dev);
				}
			}
		} else {
			devno = simple_strtoul(argv[2].str, NULL, 16);
			stor_dev = usb_stor_get_dev(devno);
			if (stor_dev != NULL &&
			    stor_dev->type != DEV_TYPE_UNKNOWN) {
				ok++;
				debug("print_part of %x\n", devno);
				print_part(stor_dev);
			}
		}
		if (!ok) {
			printf("\nno USB devices available\n");
			return 1;
		}
		return 0;
	}
	if (strcmp(argv[1].str, "read") == 0) {
		if (usb_stor_curr_dev < 0) {
			printf("no current device selected\n");
			return 1;
		}
		if (argc == 5) {
			unsigned long addr = simple_strtoul(argv[2].str, NULL, 16);
			unsigned long blk  = simple_strtoul(argv[3].str, NULL, 16);
			unsigned long cnt  = simple_strtoul(argv[4].str, NULL, 16);
			unsigned long n = 0;
			printf("\nUSB read: device %d block # %ld, count %ld"
				" ... ", usb_stor_curr_dev, blk, cnt);
			stor_dev = usb_stor_get_dev(usb_stor_curr_dev);
			if (stor_dev != NULL) {
				n = stor_dev->block_read(usb_stor_curr_dev, blk, cnt,
							 (ulong *)addr);
				printf("%ld blocks read: %s\n", n,
					(n == cnt) ? "OK" : "ERROR");
			}
			if (n == cnt)
				return 0;
			return 1;
		}
	}
	if (strcmp(argv[1].str, "write") == 0) {
		if (usb_stor_curr_dev < 0) {
			printf("no current device selected\n");
			return 1;
		}
		if (argc == 5) {
			unsigned long addr = simple_strtoul(argv[2].str, NULL, 16);
			unsigned long blk  = simple_strtoul(argv[3].str, NULL, 16);
			unsigned long cnt  = simple_strtoul(argv[4].str, NULL, 16);
			unsigned long n = 0;
			printf("\nUSB write: device %d block # %ld, count %ld"
				" ... ", usb_stor_curr_dev, blk, cnt);
			stor_dev = usb_stor_get_dev(usb_stor_curr_dev);
			if (stor_dev != NULL) {
				n = stor_dev->block_write(usb_stor_curr_dev, blk, cnt,
							(ulong *)addr);
				printf("%ld blocks write: %s\n", n,
					(n == cnt) ? "OK" : "ERROR");
			}
			if (n == cnt)
				return 0;
			return 1;
		}
	}
	if (strncmp(argv[1].str, "dev", 3) == 0) {
		if (argc == 3) {
			int dev = (int)simple_strtoul(argv[2].str, NULL, 10);
			printf("\nUSB device %d: ", dev);
			stor_dev = usb_stor_get_dev(dev);
			if (stor_dev == NULL) {
				printf("unknown device\n");
				return 1;
			}
			printf("\n    Device %d: ", dev);
			dev_print(stor_dev);
			if (stor_dev->type == DEV_TYPE_UNKNOWN)
				return 1;
			usb_stor_curr_dev = dev;
			printf("... is now current device\n");
			return 0;
		} else {
			printf("\nUSB device %d: ", usb_stor_curr_dev);
			stor_dev = usb_stor_get_dev(usb_stor_curr_dev);
			if (stor_dev != NULL) {
				dev_print(stor_dev);
				if (stor_dev->type == DEV_TYPE_UNKNOWN)
					return 1;
			}
			return 0;
		}
		return 0;
	}
#if defined(CONFIG_PARTITION_UUIDS) && defined(NAS_ENABLE)
	if (strncmp(argv[1].str, "uuid", 3) == 0) {
		if (argc == 4) {
			int dev = (int)simple_strtoul(argv[2].str, NULL, 10);
			int part = (int)simple_strtoul(argv[3].str, NULL, 10);
			stor_dev = usb_stor_get_dev(dev);
			if (stor_dev == NULL ||
			    stor_dev->type == DEV_TYPE_UNKNOWN) {
				return 1;
			}
	/* Support MBR partition only */
#ifdef CONFIG_DOS_PARTITION
			disk_partition_t info;
			if (stor_dev->part_type != PART_TYPE_DOS ||
			    get_partition_info_dos(stor_dev,part,&info) != 0)
#endif
			return 1;

                        // 0x0bda
			if(strncmp(info.uuid, RT_NAS_MAGIC, 4))
				return 1;
			printf("NAS boot: dev(%d), part(%d), uuid(%.37s)\n",
                          dev, part, info.uuid);
			setenv("nas_boot_uuid", info.uuid);
			return 0;
		}
	}
#endif
#endif /* CONFIG_USB_STORAGE */
	return 0;
}


STATIC_COMMAND_START
STATIC_COMMAND("usb", "usb command", &do_usb)
STATIC_COMMAND_END(usb);

