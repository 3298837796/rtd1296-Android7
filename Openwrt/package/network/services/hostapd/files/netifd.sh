wpa_supplicant_add_rate() {
	local var="$1"
	local val="$(($2 / 1000))"
	local sub="$((($2 / 100) % 10))"
	append $var "$val" ","
	[ $sub -gt 0 ] && append $var "."
}

hostapd_add_rate() {
	local var="$1"
	local val="$(($2 / 100))"
	append $var "$val" " "
}

hostapd_append_wep_key() {
	local var="$1"

	wep_keyidx=0
	set_default key 1
	case "$key" in
		[1234])
			for idx in 1 2 3 4; do
				local zidx
				zidx=$(($idx - 1))
				json_get_var ckey "key${idx}"
				[ -n "$ckey" ] && \
					append $var "wep_key${zidx}=$(prepare_key_wep "$ckey")" "$N$T"
			done
			wep_keyidx=$((key - 1))
		;;
		*)
			append $var "wep_key0=$(prepare_key_wep "$key")" "$N$T"
		;;
	esac
}

hostapd_add_log_config() {
	config_add_boolean \
		log_80211 \
		log_8021x \
		log_radius \
		log_wpa \
		log_driver \
		log_iapp \
		log_mlme

	config_add_int log_level
}

hostapd_common_add_device_config() {
	config_add_array basic_rate
	config_add_array supported_rates

	config_add_string country
	config_add_boolean country_ie doth
	config_add_string require_mode

	hostapd_add_log_config
}

hostapd_prepare_device_config() {
	local config="$1"
	local driver="$2"

	local base="${config%%.conf}"
	local base_cfg=

	json_get_vars country country_ie beacon_int doth require_mode

	hostapd_set_log_options base_cfg

	set_default country_ie 1
	set_default doth 0

	[ -n "$country" ] && {
		append base_cfg "country_code=$country" "$N"

		[ "$country_ie" -gt 0 ] && append base_cfg "ieee80211d=1" "$N"
		[ -n "$doth" ] && {
			[ "$hwmode" = "a" -a "$doth" -gt 0 ] && append base_cfg "ieee80211h=1" "$N"
		}
	}
	[ -n "$hwmode" ] && append base_cfg "hw_mode=$hwmode" "$N"

	local brlist= br
	json_get_values basic_rate_list basic_rate
	for br in $basic_rate_list; do
		hostapd_add_rate brlist "$br"
	done
	case "$require_mode" in
		g) brlist="60 120 240" ;;
		n) append base_cfg "require_ht=1" "$N";;
		ac) append base_cfg "require_vht=1" "$N";;
	esac

	local rlist= r
	json_get_values rate_list supported_rates
	for r in $rate_list; do
		hostapd_add_rate rlist "$r"
	done

	[ -n "$rlist" ] && append base_cfg "supported_rates=$rlist" "$N"
	[ -n "$brlist" ] && append base_cfg "basic_rates=$brlist" "$N"
	[ -n "$beacon_int" ] && append base_cfg "beacon_int=$beacon_int" "$N"

	cat > "$config" <<EOF
driver=$driver
$base_cfg
EOF
}

hostapd_common_add_bss_config() {
	config_add_string 'bssid:macaddr' 'ssid:string'
	config_add_boolean wds wmm uapsd hidden

	config_add_int maxassoc max_inactivity
	config_add_boolean disassoc_low_ack isolate short_preamble

	config_add_int \
		wep_rekey eap_reauth_period \
		wpa_group_rekey wpa_pair_rekey wpa_master_rekey

	config_add_boolean rsn_preauth auth_cache
	config_add_int ieee80211w

	config_add_string 'auth_server:host' 'server:host'
	config_add_string auth_secret
	config_add_int 'auth_port:port' 'port:port'

	config_add_string acct_server
	config_add_string acct_secret
	config_add_int acct_port

	config_add_string dae_client
	config_add_string dae_secret
	config_add_int dae_port

	config_add_string nasid
	config_add_string ownip
	config_add_string iapp_interface
	config_add_string eap_type ca_cert client_cert identity auth priv_key priv_key_pwd

	config_add_int dynamic_vlan vlan_naming
	config_add_string vlan_tagged_interface vlan_bridge

	config_add_string 'key1:wepkey' 'key2:wepkey' 'key3:wepkey' 'key4:wepkey' 'password:wpakey'

	config_add_string wpa_psk_file

	config_add_boolean wps_pushbutton wps_label ext_registrar wps_pbc_in_m1
	config_add_string wps_device_type wps_device_name wps_manufacturer wps_pin

	config_add_boolean ieee80211r pmk_r1_push
	config_add_int r0_key_lifetime reassociation_deadline
	config_add_string mobility_domain r1_key_holder
	config_add_array r0kh r1kh

	config_add_int ieee80211w_max_timeout ieee80211w_retry_timeout

	config_add_string macfilter 'macfile:file'
	config_add_array 'maclist:list(macaddr)'

	config_add_array bssid_blacklist
	config_add_array bssid_whitelist

	config_add_int mcast_rate
	config_add_array basic_rate
	config_add_array supported_rates
}

hostapd_set_bss_options() {
	local var="$1"
	local phy="$2"
	local vif="$3"

	wireless_vif_parse_encryption

	local bss_conf
	local wep_rekey wpa_group_rekey wpa_pair_rekey wpa_master_rekey wpa_key_mgmt

	json_get_vars \
		wep_rekey wpa_group_rekey wpa_pair_rekey wpa_master_rekey \
		maxassoc max_inactivity disassoc_low_ack isolate auth_cache \
		wps_pushbutton wps_label ext_registrar wps_pbc_in_m1 \
		wps_device_type wps_device_name wps_manufacturer wps_pin \
		macfilter ssid wmm uapsd hidden short_preamble rsn_preauth \
		iapp_interface

	set_default isolate 0
	set_default maxassoc 0
	set_default max_inactivity 0
	set_default short_preamble 1
	set_default disassoc_low_ack 1
	set_default hidden 0
	set_default wmm 1
	set_default uapsd 1

	append bss_conf "ctrl_interface=/var/run/hostapd"
	if [ "$isolate" -gt 0 ]; then
		append bss_conf "ap_isolate=$isolate" "$N"
	fi
	if [ "$maxassoc" -gt 0 ]; then
		append bss_conf "max_num_sta=$maxassoc" "$N"
	fi
	if [ "$max_inactivity" -gt 0 ]; then
		append bss_conf "ap_max_inactivity=$max_inactivity" "$N"
	fi

	append bss_conf "disassoc_low_ack=$disassoc_low_ack" "$N"
	append bss_conf "preamble=$short_preamble" "$N"
	append bss_conf "wmm_enabled=$wmm" "$N"
	append bss_conf "ignore_broadcast_ssid=$hidden" "$N"
	append bss_conf "uapsd_advertisement_enabled=$uapsd" "$N"

	[ "$wpa" -gt 0 ] && {
		[ -n "$wpa_group_rekey"  ] && append bss_conf "wpa_group_rekey=$wpa_group_rekey" "$N"
		[ -n "$wpa_pair_rekey"   ] && append bss_conf "wpa_ptk_rekey=$wpa_pair_rekey"    "$N"
		[ -n "$wpa_master_rekey" ] && append bss_conf "wpa_gmk_rekey=$wpa_master_rekey"  "$N"
	}

	case "$auth_type" in
		none)
			wps_possible=1
			# Here we make the assumption that if we're in open mode
			# with WPS enabled, we got to be in unconfigured state.
			wps_not_configured=1
		;;
		psk)
			json_get_vars key wpa_psk_file
			if [ ${#key} -lt 8 ]; then
				wireless_setup_vif_failed INVALID_WPA_PSK
				return 1
			elif [ ${#key} -eq 64 ]; then
				append bss_conf "wpa_psk=$key" "$N"
			else
				append bss_conf "wpa_passphrase=$key" "$N"
			fi
			[ -n "$wpa_psk_file" ] && {
				[ -e "$wpa_psk_file" ] || touch "$wpa_psk_file"
				append bss_conf "wpa_psk_file=$wpa_psk_file" "$N"
			}
			wps_possible=1
			append wpa_key_mgmt "WPA-PSK"
		;;
		eap)
			json_get_vars \
				auth_server auth_secret auth_port \
				acct_server acct_secret acct_port \
				dae_client dae_secret dae_port \
				ownip \
				eap_reauth_period dynamic_vlan \
				vlan_naming vlan_tagged_interface \
				vlan_bridge

			# legacy compatibility
			[ -n "$auth_server" ] || json_get_var auth_server server
			[ -n "$auth_port" ] || json_get_var auth_port port
			[ -n "$auth_secret" ] || json_get_var auth_secret key

			set_default auth_port 1812
			set_default acct_port 1813
			set_default dae_port 3799

			set_default vlan_naming 1

			append bss_conf "auth_server_addr=$auth_server" "$N"
			append bss_conf "auth_server_port=$auth_port" "$N"
			append bss_conf "auth_server_shared_secret=$auth_secret" "$N"

			[ -n "$acct_server" ] && {
				append bss_conf "acct_server_addr=$acct_server" "$N"
				append bss_conf "acct_server_port=$acct_port" "$N"
				[ -n "$acct_secret" ] && \
					append bss_conf "acct_server_shared_secret=$acct_secret" "$N"
			}

			[ -n "$eap_reauth_period" ] && append bss_conf "eap_reauth_period=$eap_reauth_period" "$N"

			[ -n "$dae_client" -a -n "$dae_secret" ] && {
				append bss_conf "radius_das_port=$dae_port" "$N"
				append bss_conf "radius_das_client=$dae_client $dae_secret" "$N"
			}

			[ -n "$ownip" ] && append bss_conf "own_ip_addr=$ownip" "$N"
			append bss_conf "eapol_key_index_workaround=1" "$N"
			append bss_conf "ieee8021x=1" "$N"
			append wpa_key_mgmt "WPA-EAP"

			[ -n "$dynamic_vlan" ] && {
				append bss_conf "dynamic_vlan=$dynamic_vlan" "$N"
				append bss_conf "vlan_naming=$vlan_naming" "$N"
				[ -n "$vlan_bridge" ] && \
					append bss_conf "vlan_bridge=$vlan_bridge" "$N"
				[ -n "$vlan_tagged_interface" ] && \
					append bss_conf "vlan_tagged_interface=$vlan_tagged_interface" "$N"
			}
		;;
		wep)
			local wep_keyidx=0
			json_get_vars key
			hostapd_append_wep_key bss_conf
			append bss_conf "wep_default_key=$wep_keyidx" "$N"
			[ -n "$wep_rekey" ] && append bss_conf "wep_rekey_period=$wep_rekey" "$N"
		;;
	esac

	local auth_algs=$((($auth_mode_shared << 1) | $auth_mode_open))
	append bss_conf "auth_algs=${auth_algs:-1}" "$N"
	append bss_conf "wpa=$wpa" "$N"
	[ -n "$wpa_pairwise" ] && append bss_conf "wpa_pairwise=$wpa_pairwise" "$N"

	set_default wps_pushbutton 0
	set_default wps_label 0
	set_default wps_pbc_in_m1 0

	config_methods=
	[ "$wps_pushbutton" -gt 0 ] && append config_methods push_button
	[ "$wps_label" -gt 0 ] && append config_methods "label display keypad"

	[ -n "$wps_possible" -a -n "$config_methods" ] && {
		set_default ext_registrar 0
		set_default wps_device_type "6-0050F204-1"
		set_default wps_device_name "OpenWrt AP"
		set_default wps_manufacturer "openwrt.org"

		wps_state=2
		[ -n "$wps_not_configured" ] && wps_state=1

		[ "$ext_registrar" -gt 0 -a -n "$network_bridge" ] && append bss_conf "upnp_iface=$network_bridge" "$N"

		append bss_conf "eap_server=1" "$N"
		[ -n "$wps_pin" ] && append bss_conf "ap_pin=$wps_pin" "$N"
		append bss_conf "wps_state=$wps_state" "$N"
		append bss_conf "ap_setup_locked=0" "$N"
		append bss_conf "device_type=$wps_device_type" "$N"
		append bss_conf "device_name=$wps_device_name" "$N"
		append bss_conf "manufacturer=$wps_manufacturer" "$N"
		append bss_conf "config_methods=$config_methods" "$N"
		append bss_conf "wps_independent=1" "$N"
		[ "$wps_pbc_in_m1" -gt 0 ] && append bss_conf "pbc_in_m1=$wps_pbc_in_m1" "$N"
	}

	append bss_conf "ssid=$ssid" "$N"
	[ -n "$network_bridge" ] && append bss_conf "bridge=$network_bridge" "$N"
	[ -n "$iapp_interface" ] && {
		iapp_interface="$(uci_get_state network "$iapp_interface" ifname "$iapp_interface")"
		[ -n "$iapp_interface" ] && append bss_conf "iapp_interface=$iapp_interface" "$N"
	}

	if [ "$wpa" -ge "1" ]; then
		json_get_vars nasid ieee80211r
		set_default ieee80211r 0
		[ -n "$nasid" ] && append bss_conf "nas_identifier=$nasid" "$N"

		if [ "$ieee80211r" -gt "0" ]; then
			json_get_vars mobility_domain r0_key_lifetime r1_key_holder \
			reassociation_deadline pmk_r1_push
			json_get_values r0kh r0kh
			json_get_values r1kh r1kh

			set_default mobility_domain "4f57"
			set_default r0_key_lifetime 10000
			set_default r1_key_holder "00004f577274"
			set_default reassociation_deadline 1000
			set_default pmk_r1_push 0

			append bss_conf "mobility_domain=$mobility_domain" "$N"
			append bss_conf "r0_key_lifetime=$r0_key_lifetime" "$N"
			append bss_conf "r1_key_holder=$r1_key_holder" "$N"
			append bss_conf "reassociation_deadline=$reassociation_deadline" "$N"
			append bss_conf "pmk_r1_push=$pmk_r1_push" "$N"

			for kh in $r0kh; do
				append bss_conf "r0kh=${kh//,/ }" "$N"
			done
			for kh in $r1kh; do
				append bss_conf "r1kh=${kh//,/ }" "$N"
			done

			[ "$wpa_key_mgmt" != "${wpa_key_mgmt/EAP/}" ] && append wpa_key_mgmt "FT-EAP"
			[ "$wpa_key_mgmt" != "${wpa_key_mgmt/PSK/}" ] && append wpa_key_mgmt "FT-PSK"
		fi

		[ -n "$wpa_key_mgmt" ] && append bss_conf "wpa_key_mgmt=$wpa_key_mgmt" "$N"
	fi

	if [ "$wpa" -ge "2" ]; then
		if [ -n "$network_bridge" -a "$rsn_preauth" = 1 ]; then
			set_default auth_cache 1
			append bss_conf "rsn_preauth=1" "$N"
			append bss_conf "rsn_preauth_interfaces=$network_bridge" "$N"
		else
			set_default auth_cache 0
		fi

		append bss_conf "okc=$auth_cache" "$N"
		[ "$auth_cache" = 0 ] && append bss_conf "disable_pmksa_caching=1" "$N"

		# RSN -> allow management frame protection
		json_get_var ieee80211w ieee80211w
		case "$ieee80211w" in
			[012])
				json_get_vars ieee80211w_max_timeout ieee80211w_retry_timeout
				append bss_conf "ieee80211w=$ieee80211w" "$N"
				[ "$ieee80211w" -gt "0" ] && {
					[ -n "$ieee80211w_max_timeout" ] && \
						append bss_conf "assoc_sa_query_max_timeout=$ieee80211w_max_timeout" "$N"
					[ -n "$ieee80211w_retry_timeout" ] && \
						append bss_conf "assoc_sa_query_retry_timeout=$ieee80211w_retry_timeout" "$N"
				}
			;;
		esac
	fi

	_macfile="/var/run/hostapd-$ifname.maclist"
	case "$macfilter" in
		allow)
			append bss_conf "macaddr_acl=1" "$N"
			append bss_conf "accept_mac_file=$_macfile" "$N"
		;;
		deny)
			append bss_conf "macaddr_acl=0" "$N"
			append bss_conf "deny_mac_file=$_macfile" "$N"
		;;
		*)
			_macfile=""
		;;
	esac

	[ -n "$_macfile" ] && {
		json_get_vars macfile
		json_get_values maclist maclist

		rm -f "$_macfile"
		(
			for mac in $maclist; do
				echo "$mac"
			done
			[ -n "$macfile" -a -f "$macfile" ] && cat "$macfile"
		) > "$_macfile"
	}

	append "$var" "$bss_conf" "$N"
	return 0
}

hostapd_set_log_options() {
	local var="$1"

	local log_level log_80211 log_8021x log_radius log_wpa log_driver log_iapp log_mlme
	json_get_vars log_level log_80211 log_8021x log_radius log_wpa log_driver log_iapp log_mlme

	set_default log_level 2
	set_default log_80211  1
	set_default log_8021x  1
	set_default log_radius 1
	set_default log_wpa    1
	set_default log_driver 1
	set_default log_iapp   1
	set_default log_mlme   1

	local log_mask=$(( \
		($log_80211  << 0) | \
		($log_8021x  << 1) | \
		($log_radius << 2) | \
		($log_wpa    << 3) | \
		($log_driver << 4) | \
		($log_iapp   << 5) | \
		($log_mlme   << 6)   \
	))

	append "$var" "logger_syslog=$log_mask" "$N"
	append "$var" "logger_syslog_level=$log_level" "$N"
	append "$var" "logger_stdout=$log_mask" "$N"
	append "$var" "logger_stdout_level=$log_level" "$N"

	return 0
}

_wpa_supplicant_common() {
	local ifname="$1"

	_rpath="/var/run/wpa_supplicant"
	_config="${_rpath}-$ifname.conf"
	_gctrlpath="@abstract:wpa_$ifname"
}

wpa_supplicant_teardown_interface() {
	_wpa_supplicant_common "$1"
	rm -rf "$_rpath/$1" "$_config"
}

wpa_supplicant_prepare_interface() {
	local ifname="$1"
	_w_driver="$2"

	_wpa_supplicant_common "$1"

	json_get_vars mode wds

	[ -n "$network_bridge" ] && {
		fail=
		case "$mode" in
			adhoc)
				fail=1
			;;
			sta)
				[ "$wds" = 1 ] || fail=1
			;;
		esac

		#marked for realtek propietary
		#[ -n "$fail" ] && {
		#	wireless_setup_vif_failed BRIDGE_NOT_ALLOWED
		#	return 1
		#}
	}

	local ap_scan=

	_w_mode="$mode"
	_w_modestr=

	[[ "$mode" = adhoc ]] && {
		ap_scan="ap_scan=2"

		_w_modestr="mode=1"
	}

	wpa_supplicant_teardown_interface "$ifname"
	cat > "$_config" <<EOF
$ap_scan
EOF
	return 0
}

wpa_supplicant_add_network() {
	local ifname="$1"

	_wpa_supplicant_common "$1"
	wireless_vif_parse_encryption

	json_get_vars \
		ssid bssid key \
		basic_rate mcast_rate \
		ieee80211w

	local key_mgmt='NONE'
	local enc_str=
	local network_data=
	local T="	"

	local wpa_key_mgmt="WPA-PSK"
	local scan_ssid="scan_ssid=1"
	local freq

	[[ "$_w_mode" = "adhoc" ]] && {
		append network_data "mode=1" "$N$T"
		[ -n "$channel" ] && {
			freq="$(get_freq "$phy" "$channel")"
			append network_data "fixed_freq=1" "$N$T"
			append network_data "frequency=$freq" "$N$T"
		}

		scan_ssid="scan_ssid=0"

		[ "$_w_driver" = "nl80211" ] ||	wpa_key_mgmt="WPA-NONE"
	}

	[[ "$_w_mode" = "mesh" ]] && {
		append network_data "mode=5" "$N$T"
		[ -n "$channel" ] && {
			freq="$(get_freq "$phy" "$channel")"
			append network_data "frequency=$freq" "$N$T"
		}
		wpa_key_mgmt="SAE"
		scan_ssid=""
	}

	[[ "$_w_mode" = "adhoc" -o "$_w_mode" = "mesh" ]] && append network_data "$_w_modestr" "$N$T"

	case "$auth_type" in
		none) ;;
		wep)
			local wep_keyidx=0
			hostapd_append_wep_key network_data
			append network_data "wep_tx_keyidx=$wep_keyidx" "$N$T"
			local auth_alg=$((($auth_mode_shared << 1) | $auth_mode_open))
			if [ $auth_alg -eq 2 ]; then
				append network_data "auth_alg=SHARED" "$N$T"
			elif [ $auth_alg -eq 1 ]; then
				append network_data "auth_alg=OPEN" "$N$T"
			else
				echo "auth_alg=$auth_alg"
			fi
		;;
		psk)
			local passphrase

			key_mgmt="$wpa_key_mgmt"
			if [ ${#key} -eq 64 ]; then
				passphrase="psk=${key}"
			else
				passphrase="psk=\"${key}\""
			fi
			append network_data "$passphrase" "$N$T"
		;;
		eap)
			key_mgmt='WPA-EAP'

			json_get_vars eap_type identity ca_cert
			[ -n "$ca_cert" ] && append network_data "ca_cert=\"$ca_cert\"" "$N$T"
			[ -n "$identity" ] && append network_data "identity=\"$identity\"" "$N$T"
			case "$eap_type" in
				tls)
					json_get_vars client_cert priv_key priv_key_pwd
					append network_data "client_cert=\"$client_cert\"" "$N$T"
					append network_data "private_key=\"$priv_key\"" "$N$T"
					append network_data "private_key_passwd=\"$priv_key_pwd\"" "$N$T"
				;;
				peap|ttls)
					json_get_vars auth password
					set_default auth MSCHAPV2
					append network_data "phase2=\"$auth\"" "$N$T"
					append network_data "password=\"$password\"" "$N$T"
				;;
			esac
			append network_data "eap=$(echo $eap_type | tr 'a-z' 'A-Z')" "$N$T"
		;;
	esac

	[ "$mode" = mesh ] || {
		case "$wpa" in
			1)
				append network_data "proto=WPA" "$N$T"
			;;
			2)
				append network_data "proto=RSN" "$N$T"
			;;
		esac

		case "$ieee80211w" in
			[012])
				[ "$wpa" -ge 2 ] && append network_data "ieee80211w=$ieee80211w" "$N$T"
			;;
		esac
	}
	local beacon_int brates mrate
	[ -n "$bssid" ] && append network_data "bssid=$bssid" "$N$T"
	[ -n "$beacon_int" ] && append network_data "beacon_int=$beacon_int" "$N$T"

	local bssid_blacklist bssid_whitelist
	json_get_values bssid_blacklist bssid_blacklist
	json_get_values bssid_whitelist bssid_whitelist

	[ -n "$bssid_blacklist" ] && append network_data "bssid_blacklist=$bssid_blacklist" "$N$T"
	[ -n "$bssid_whitelist" ] && append network_data "bssid_whitelist=$bssid_whitelist" "$N$T"

	[ -n "$basic_rate" ] && {
		local br rate_list=
		for br in $basic_rate; do
			wpa_supplicant_add_rate rate_list "$br"
		done
		[ -n "$rate_list" ] && append network_data "rates=$rate_list" "$N$T"
	}

	[ -n "$mcast_rate" ] && {
		local mc_rate=
		wpa_supplicant_add_rate mc_rate "$mcast_rate"
		append network_data "mcast_rate=$mc_rate" "$N$T"
	}

	local ht_str
	[[ "$_w_mode" = adhoc ]] || ibss_htmode=
	[ -n "$ibss_htmode" ] && append network_data "htmode=$ibss_htmode" "$N$T"

	if [ "$ssid" != "" ] || [ "$bssid" != "" ] ; then

	cat >> "$_config" <<EOF
network={
	$scan_ssid
	ssid="$ssid"
	key_mgmt=$key_mgmt
	$network_data
}
EOF
	fi

	return 0
}

wpa_supplicant_run() {
	local ifname="$1"; shift

	_wpa_supplicant_common "$ifname"

	/usr/sbin/wpa_supplicant -B \
		${network_bridge:+-b $network_bridge} \
		-P "/var/run/wpa_supplicant-${ifname}.pid" \
		-D ${_w_driver:-wext} \
		-i "$ifname" \
		-c "$_config" \
		-C "$_rpath" \
		-g "$_gctrlpath" \
		"$@"

	ret="$?"
	wireless_add_process "$(cat "/var/run/wpa_supplicant-${ifname}.pid")" /usr/sbin/wpa_supplicant 1

	[ "$ret" != 0 ] && wireless_setup_vif_failed WPA_SUPPLICANT_FAILED

	return $ret
}

hostapd_common_cleanup() {
	killall hostapd wpa_supplicant meshd-nl80211
}
