package strategy

import (
	pb "im-connect-go/internal/proto"

	"github.com/panjf2000/gnet/v2"
)

// GnetProtoMsgHandlerStrategy 定义了适配 gnet 的新策略接口
type GnetProtoMsgHandlerStrategy interface {
	Exchange(c gnet.Conn, request *pb.ImProtoRequest) error
}
