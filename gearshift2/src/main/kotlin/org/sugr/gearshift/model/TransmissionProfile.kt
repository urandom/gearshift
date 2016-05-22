package org.sugr.gearshift.model

class TransmissionProfile : Profile() {
    init {
        port = 9091
        path = "/transmission/rpc"
    }
}
