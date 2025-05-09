#! /usr/bin/perl -w

################################################################################
#
# print input binary in byte-reversed order
#
# Usage:
#	./reverse.pl <input_file>
#
################################################################################

if( $#ARGV != -1 ) {
	if (!open BINARY, $ARGV[0]) {
		print "Can't not open input file: $!";
	}
	else {
		my $size = -s BINARY;
		my $current = 0;
	
		# read each byte
		while ($current++ < $size) {
			read(BINARY, $byte, 1);
			push(@in, $byte);
		}
		
		# print reversely
		@in = reverse @in;
		foreach (@in) {
			print $_;
		}

		close BINARY;
	}
}
else {
	die "Usage:\n\treverse <input_file>\n";
}
