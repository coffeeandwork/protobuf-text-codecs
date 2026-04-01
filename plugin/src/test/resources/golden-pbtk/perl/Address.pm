package Example::Address;

use strict;
use warnings;
use MIME::Base64 qw(encode_base64 decode_base64);
use URI::Escape qw(uri_escape_utf8 uri_unescape);

sub new {
    my ($class, %%args) = @_;
    my $self = bless {}, $class;
    $self->{street} = "";
    $self->{city} = "";
    $self->{state} = "";
    $self->{zip} = 0;
    return $self;
}

sub street {
    my ($self, $value) = @_;
    if (defined $value) {
        $self->{street} = $value;
    }
    return $self->{street};
}

sub city {
    my ($self, $value) = @_;
    if (defined $value) {
        $self->{city} = $value;
    }
    return $self->{city};
}

sub state {
    my ($self, $value) = @_;
    if (defined $value) {
        $self->{state} = $value;
    }
    return $self->{state};
}

sub zip {
    my ($self, $value) = @_;
    if (defined $value) {
        $self->{zip} = $value;
    }
    return $self->{zip};
}

sub _append_pbtk_fields {
    my ($self, $parts) = @_;
    push @{$parts}, "!1s" . uri_escape_utf8($self->{street});
    push @{$parts}, "!2s" . uri_escape_utf8($self->{city});
    push @{$parts}, "!3s" . uri_escape_utf8($self->{state});
    push @{$parts}, "!4i" . $self->{zip};
}

sub _count_pbtk_fields {
    my ($self) = @_;
    my $count = 0;
    $count += 1;
    $count += 1;
    $count += 1;
    $count += 1;
    return $count;
}

sub encode {
    my ($self) = @_;
    my @parts;
    $self->_append_pbtk_fields(\@parts);
    return join("", @parts);
}

sub _parse_pbtk_tokens {
    my ($class, $tokens, $field_count, $offset) = @_;
    my $obj = $class->new();
    my $consumed = 0;
    while ($consumed < $field_count && $offset->[0] < scalar @{$tokens}) {
        my $token = $tokens->[$offset->[0]];
        my $num_end = 0;
        $num_end++ while ($num_end < length($token) && substr($token, $num_end, 1) =~ /\d/);
        if ($num_end == 0 || $num_end >= length($token)) {
            $offset->[0]++;
            $consumed++;
            next;
        }
        my $field_num = int(substr($token, 0, $num_end));
        my $type_char = substr($token, $num_end, 1);
        my $value = substr($token, $num_end + 1);
        if ($field_num == 1) {
            $obj->{street} = uri_unescape($value);
            $offset->[0]++;
            $consumed++;
        } elsif ($field_num == 2) {
            $obj->{city} = uri_unescape($value);
            $offset->[0]++;
            $consumed++;
        } elsif ($field_num == 3) {
            $obj->{state} = uri_unescape($value);
            $offset->[0]++;
            $consumed++;
        } elsif ($field_num == 4) {
            $obj->{zip} = int($value);
            $offset->[0]++;
            $consumed++;
        } else {
            $offset->[0]++;
            $consumed++;
        }
    }
    return $obj;
}

sub decode {
    my ($class, $input_str) = @_;
    if (!defined($input_str) || $input_str eq "") {
        return $class->new();
    }
    my $tokens = $class->_tokenize_pbtk($input_str);
    my $offset = [0];
    return $class->_parse_pbtk_tokens($tokens, scalar @{$tokens}, $offset);
}

sub _tokenize_pbtk {
    my ($class, $input_str) = @_;
    my @tokens;
    my $i = (length($input_str) > 0 && substr($input_str, 0, 1) eq "!") ? 1 : 0;
    while ($i < length($input_str)) {
        my $nxt = index($input_str, "!", $i);
        if ($nxt < 0) {
            push @tokens, substr($input_str, $i);
            last;
        }
        push @tokens, substr($input_str, $i, $nxt - $i);
        $i = $nxt + 1;
    }
    return \@tokens;
}

sub to_string {
    my ($self) = @_;
    return ref($self) . "(" . "street=" . (defined($self->{street}) ? $self->{street} : "undef") . ", " . "city=" . (defined($self->{city}) ? $self->{city} : "undef") . ", " . "state=" . (defined($self->{state}) ? $self->{state} : "undef") . ", " . "zip=" . (defined($self->{zip}) ? $self->{zip} : "undef") . ")";
}

1;
