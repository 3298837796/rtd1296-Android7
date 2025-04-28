#
# Copyright (C) 2012 OpenWrt.org
#
# This is free software, licensed under the GNU General Public License v2.
# See /LICENSE for more information.
#
define Profile/tarax-4GB
  NAME:=Tarax 4GB board
endef

define Profile/tarax-4GB/Description
	Tarax board V0.1 4GB
endef

$(eval $(call Profile,tarax-4GB))
