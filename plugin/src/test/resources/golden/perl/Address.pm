package Example::Address;

use strict;
use warnings;
use JSON qw(encode_json decode_json);
use MIME::Base64 qw(encode_base64 decode_base64);

sub new {
    my ($class, %args) = @_;
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

sub serialize {
    my ($self) = @_;
    my @result;
    push @result, $self->{street};
    push @result, $self->{city};
    push @result, $self->{state};
    push @result, $self->{zip};
    return \@result;
}

sub to_json_string {
    my ($self) = @_;
    return encode_json($self->serialize());
}

sub to_json_bytes {
    my ($self) = @_;
    return $self->to_json_string();
}

sub deserialize {
    my ($class, $data) = @_;
    my $obj = $class->new();
    my $size = scalar @{$data};
    if ($size > 0 && defined($data->[0])) {
        $obj->{street} = "" . $data->[0];
    }
    if ($size > 1 && defined($data->[1])) {
        $obj->{city} = "" . $data->[1];
    }
    if ($size > 2 && defined($data->[2])) {
        $obj->{state} = "" . $data->[2];
    }
    if ($size > 3 && defined($data->[3])) {
        $obj->{zip} = int($data->[3]);
    }
    return $obj;
}

sub from_json_string {
    my ($class, $json_str) = @_;
    return $class->deserialize(decode_json($json_str));
}

sub from_json_bytes {
    my ($class, $data) = @_;
    return $class->from_json_string($data);
}

1;
