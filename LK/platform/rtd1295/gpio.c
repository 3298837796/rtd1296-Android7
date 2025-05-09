#include <stdio.h>
#include <platform/io.h>
#include <platform/gpio.h>
#include <platform/rbus/mis_reg.h>
#include <platform/rbus/iso_reg.h>


#define gpio_assert(x)		printf("assert assert assert!!!! GPIO_NUM=%d\n", GPIO_NUM)
#define iso_gpio_assert(x)	printf("assert assert assert!!!! ISOGPIO_NUM=%d\n", ISOGPIO_NUM)

static void set_pconf_pullsel(unsigned int GPIO_NUM, unsigned char pull_sel)
{
	//volatile int regAddr,val,mask;
	unsigned int regAddr,val,mask;

	if(pin_regmap[GPIO_NUM].pcof_regoff==PCOF_UNSUPPORT)
	{
		printf("[set_pconf_pullsel] GPIO#%d pin not support pull select\n",GPIO_NUM);
		return;
	}

	regAddr = pin_regmap[GPIO_NUM].pmux_base+pin_regmap[GPIO_NUM].pcof_regoff;
	mask = 0x3<<pin_regmap[GPIO_NUM].pcof_regbit;// 2bit mask

	val = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);
	switch(pull_sel)
	{
		case PULL_DISABLE:
			val = (val & ~mask);
			break;
		case PULL_DOWN:
			val = (val & ~mask)|(0x2<<pin_regmap[GPIO_NUM].pcof_regbit);
			break;
		case PULL_UP:
			val = (val & ~mask)|(0x3<<pin_regmap[GPIO_NUM].pcof_regbit);
			break;
	}

	rtd_outl((volatile unsigned int *)(uintptr_t)regAddr,val);
}

static void set_gpio_pinmux(int GPIO_NUM)
{
	//volatile int regAddr,val,mask;
	unsigned int regAddr,val,mask;

	if((GPIO_NUM<0)||(GPIO_NUM>135))
	{
		printf("[%s] No GPIO#%d pin\n",__FUNCTION__,GPIO_NUM);
		return;
	}
	else if(pin_regmap[GPIO_NUM].pmux_regoff==PMUX_UNSUPPORT)
		return;

	// Get pinmux address and mask
	regAddr = pin_regmap[GPIO_NUM].pmux_base+pin_regmap[GPIO_NUM].pmux_regoff;
	mask =	pin_regmap[GPIO_NUM].pmux_regbitmsk << pin_regmap[GPIO_NUM].pmux_regbit;

	// Get original pinmux setting
	val = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);
	val = val & (~mask);//All GPIO pinmux value is 0

	rtd_outl((volatile unsigned int *)(uintptr_t)regAddr,val);
}

/*======================================================================
 * Func : getGPIO
 *
 * Desc : Set MISC GPIO as input and get input value
 *
 * Parm : GPIO_NUM: MISC GPIO number(0~100)
 *
 * Retn : 0 (Low) / 1 (High)/ -1 (Fail)
 *======================================================================*/
int getGPIO(int GPIO_NUM) {
	int bitOffset;
	//volatile int regAddr;
	unsigned int regAddr;
	int regValue;

	set_gpio_pinmux(GPIO_NUM);

	if(GPIO_NUM <= 31) {
		bitOffset = GPIO_NUM;

		// Set Direction to Input
		regAddr = (MIS_GP0DIR_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);
		regValue = regValue & (~(0x01 << bitOffset));
		rtd_outl((volatile unsigned int *)(uintptr_t)regAddr, regValue);

		// Get Value
		regAddr = (MIS_GP0DATI_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);
		if(regValue & (0x01 << bitOffset))
			return 1;
		else
			return 0;
	}
	else if(GPIO_NUM >= 32 && GPIO_NUM <= 63) {
		bitOffset = GPIO_NUM - 32;

		// Set Direction to Input
		regAddr = (MIS_GP1DIR_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);
		regValue = regValue & (~(0x01 << bitOffset));
		rtd_outl((volatile unsigned int *)(uintptr_t)regAddr, regValue);

		// Get Value
		regAddr = (MIS_GP1DATI_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);
		if(regValue & (0x01 << bitOffset))
			return 1;
		else
			return 0;
	}
	else if(GPIO_NUM >= 64 && GPIO_NUM <= 95) {
		bitOffset = GPIO_NUM - 64;

		// Set Direction to Input
		regAddr = (MIS_GP2DIR_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);
		regValue = regValue & (~(0x01 << bitOffset));
		rtd_outl((volatile unsigned int *)(uintptr_t)regAddr, regValue);

		// Get Value
		regAddr = (MIS_GP2DATI_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);
		if(regValue & (0x01 << bitOffset))
			return 1;
		else
			return 0;
	}
	else if(GPIO_NUM >= 96 && GPIO_NUM <= 100) {
		bitOffset = GPIO_NUM - 96;

		// Set Direction to Input
		regAddr = (MIS_GP3DIR_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);
		regValue = regValue & (~(0x01 << bitOffset));
		rtd_outl((volatile unsigned int *)(uintptr_t)regAddr, regValue);

		// Get Value
		regAddr = (MIS_GP3DATI_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);
		if(regValue & (0x01 << bitOffset))
			return 1;
		else
			return 0;
	}
	else { // no such GPIO pin!
		printf("no GPIO#%d pin\n",GPIO_NUM);
		return -1;
	}
}


/*======================================================================
 * Func : setGPIO
 *
 * Desc : Set MISC GPIO as output and assign ouput level
 *
 * Parm : GPIO_NUM: MISC GPIO number(0~100)
 *            value: 0 (Output Low)/ 1 (Output High)
 *
 * Retn : 0 (OK) / -1 (FAIL)
 *======================================================================*/
int setGPIO(int GPIO_NUM, int value) {
	int bitOffset;
	//volatile int regAddr;
	unsigned int regAddr;
	int regValue;

	set_gpio_pinmux(GPIO_NUM);

	if(GPIO_NUM <= 31) {
		bitOffset = GPIO_NUM;

		// Set Direction to Ouput
		regAddr = (MIS_GP0DIR_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);
		regValue = regValue | (0x01 << bitOffset);
		rtd_outl((volatile unsigned int *)(uintptr_t)regAddr, regValue);

		// Set Value
		regAddr = (MIS_GP0DATO_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);

		if(value)
			regValue = regValue | (0x01 << bitOffset);	// set to 1
		else
			regValue = regValue & (~(0x01 << bitOffset)); // set to 0

		rtd_outl((volatile unsigned int *)(uintptr_t)regAddr, regValue);

		return 0;
	}
	else if(GPIO_NUM >= 32 && GPIO_NUM <= 63) {
		bitOffset = GPIO_NUM - 32;

		// Set Direction to Ouput
		regAddr = (MIS_GP1DIR_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);
		regValue = regValue | (0x01 << bitOffset);
		rtd_outl((volatile unsigned int *)(uintptr_t)regAddr, regValue);

		// Set Value
		regAddr = (MIS_GP1DATO_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);

		if(value)
			regValue = regValue | (0x01 << bitOffset);	// set to 1
		else
			regValue = regValue & (~(0x01 << bitOffset)); // set to 0

		rtd_outl((volatile unsigned int *)(uintptr_t)regAddr, regValue);

		return 0;
	}
	else if(GPIO_NUM >= 64 && GPIO_NUM <= 95) {
		bitOffset = GPIO_NUM - 64;

		// Set Direction to Ouput
		regAddr = (MIS_GP2DIR_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);
		regValue = regValue | (0x01 << bitOffset);
		rtd_outl((volatile unsigned int *)(uintptr_t)regAddr, regValue);

		// Set Value
		regAddr = (MIS_GP2DATO_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);

		if(value)
			regValue = regValue | (0x01 << bitOffset);	// set to 1
		else
			regValue = regValue & (~(0x01 << bitOffset)); // set to 0

		rtd_outl((volatile unsigned int *)(uintptr_t)regAddr, regValue);

		return 0;
	}
	else if(GPIO_NUM >= 96 && GPIO_NUM <= 100) {
		bitOffset = GPIO_NUM - 96;

		// Set Direction to Ouput
		regAddr = (MIS_GP3DIR_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);
		regValue = regValue | (0x01 << bitOffset);
		rtd_outl((volatile unsigned int *)(uintptr_t)regAddr, regValue);

		// Set Value
		regAddr = (MIS_GP3DATO_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);

		if(value)
			regValue = regValue | (0x01 << bitOffset);	// set to 1
		else
			regValue = regValue & (~(0x01 << bitOffset)); // set to 0

		rtd_outl((volatile unsigned int *)(uintptr_t)regAddr, regValue);

		return 0;
	}
	else { // no such GPIO pin!
		printf("no GPIO#%d pin\n",GPIO_NUM);
		return -1;
	}
}

/*======================================================================
 * Func : getISOGPIO
 *
 * Desc : Set ISO GPIO as input and get input value
 *
 * Parm : ISOGPIO_NUM: ISO GPIO number(0~34)
 *
 * Retn : 0 (Low) / 1 (High)/ -1(Fail)
 *======================================================================*/
int getISOGPIO(int ISOGPIO_NUM)
{
	int bitOffset;
	//volatile int regAddr;
	unsigned int regAddr;
	int regValue;

	set_gpio_pinmux(ISOGPIO_NUM+101);

	if(ISOGPIO_NUM <= 31) {
		bitOffset = ISOGPIO_NUM;

		// Set Direction to Input
		regAddr = (ISO_GPDIR_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);
		regValue = regValue & (~(0x01 << bitOffset));
		rtd_outl((volatile unsigned int *)(uintptr_t)regAddr, regValue);

		// Get Value
		regAddr = (ISO_GPDATI_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);
		if(regValue & (0x01 << bitOffset))
			return 1;
		else
			return 0;
	}
	else if(ISOGPIO_NUM >= 32 && ISOGPIO_NUM <= 34) {
		bitOffset = ISOGPIO_NUM - 32;

		// Set Direction to Input
		regAddr = (ISO_GPDIR_1_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);
		regValue = regValue & (~(0x01 << bitOffset));
		rtd_outl((volatile unsigned int *)(uintptr_t)regAddr, regValue);

		// Get Value
		regAddr = (ISO_GPDATI_1_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);
		if(regValue & (0x01 << bitOffset))
			return 1;
		else
			return 0;
	}
	else { // no such ISO GPIO pin!
		printf("no ISOGPIO#%d pin\n",ISOGPIO_NUM);
		return -1;
	}
}


/*======================================================================
 * Func : setISOGPIO
 *
 * Desc : Set ISO GPIO as output and assign ouput level
 *
 * Parm : ISOGPIO_NUM: ISO GPIO number(0~34)
 *            value: 0 (Output Low)/ 1 (Output High)
 *
 * Retn : 0 (OK) / -1 (FAIL)
 *======================================================================*/
int setISOGPIO(int ISOGPIO_NUM, int value)
{
	int bitOffset;
	//volatile int regAddr;
	unsigned int regAddr;
	int regValue;

	set_gpio_pinmux(ISOGPIO_NUM+101);

	if(ISOGPIO_NUM <= 31) {
		bitOffset = ISOGPIO_NUM;

		// Set Direction to Ouput
		regAddr = (ISO_GPDIR_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);
		regValue = regValue | (0x01 << bitOffset);
		rtd_outl((volatile unsigned int *)(uintptr_t)regAddr, regValue);

		// Set Value
		regAddr = (ISO_GPDATO_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);

		if(value)
			regValue = regValue | (0x01 << bitOffset);  // set to 1
		else
			regValue = regValue & (~(0x01 << bitOffset)); // set to 0

		rtd_outl((volatile unsigned int *)(uintptr_t)regAddr, regValue);

		return 0;
	}	
	else if(ISOGPIO_NUM >= 32 && ISOGPIO_NUM <= 34) {
		bitOffset = ISOGPIO_NUM - 32;

		// Set Direction to Ouput
		regAddr = (ISO_GPDIR_1_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);
		regValue = regValue | (0x01 << bitOffset);
		rtd_outl((volatile unsigned int *)(uintptr_t)regAddr, regValue);

		// Set Value
		regAddr = (ISO_GPDATO_1_reg);
		regValue = rtd_inl((volatile unsigned int *)(uintptr_t)regAddr);

		if(value)
			regValue = regValue | (0x01 << bitOffset);  // set to 1
		else
			regValue = regValue & (~(0x01 << bitOffset)); // set to 0

		rtd_outl((volatile unsigned int *)(uintptr_t)regAddr, regValue);

		return 0;
	}	
	else { // no such ISO GPIO pin!
		printf("no ISOGPIO#%d pin\n",ISOGPIO_NUM);
		return -1;
	}
}

/*======================================================================
 * Func : setGPIO_pullsel
 *
 * Desc : Set MISC GPIO pull disable/down/up
 *
 * Parm : GPIO_NUM: MISC GPIO number(0~100)
 *            pull_sel: 0 (Pull Disable)/ 1 (Pull Down)/ 2 (Pull Up)
 *
 * Retn : 0 (OK) / -1 (FAIL)
 *======================================================================*/
int setGPIO_pullsel(unsigned int GPIO_NUM, unsigned char pull_sel)
{
	if(GPIO_NUM>100)
	{
		printf("[%s] No GPIO#%d pin\n",__FUNCTION__,GPIO_NUM);
		return -1;
	}
	else if(pull_sel>2)
	{
		printf("[%s] Invalid pull_sel value, GPIO#%d\n",__FUNCTION__,GPIO_NUM);
		return -1;
	}

	set_pconf_pullsel(GPIO_NUM, pull_sel);
	return 0;
}

/*======================================================================
 * Func : setISOGPIO_pullsel
 *
 * Desc : Set ISO GPIO pull disable/down/up
 *
 * Parm : ISOGPIO_NUM: ISO GPIO number(0~34)
 *            pull_sel: 0 (Pull Disable)/ 1 (Pull Down)/ 2 (Pull Up)
 *
 * Retn : 0 (OK) / -1 (FAIL)
 *======================================================================*/
int setISOGPIO_pullsel(int ISOGPIO_NUM, unsigned char pull_sel)
{
	if((ISOGPIO_NUM<0)||(ISOGPIO_NUM>34))
	{
		printf("[%s] No ISOGPIO#%d pin\n",__FUNCTION__,ISOGPIO_NUM);
		return -1;
	}
	else if(pull_sel>2)
	{
		printf("[%s] Invalid pull_sel value, ISOGPIO#%d\n",__FUNCTION__,ISOGPIO_NUM);
		return -1;
	}

	set_pconf_pullsel(ISOGPIO_NUM+101, pull_sel);
	return 0;
}

