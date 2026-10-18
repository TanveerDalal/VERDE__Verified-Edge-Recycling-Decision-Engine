import sys, torch, torchvision.models as tv
from torch.export import export
from executorch.exir import to_edge_transform_and_lower
from executorch.backends.xnnpack.partition.xnnpack_partitioner import XnnpackPartitioner

ck = torch.load("verde_model.pth", map_location="cpu")
print("checkpoint keys:", list(ck.keys()))
names = ck["class_names"]
state = ck.get("model_state") or ck.get("state_dict") or ck.get("model")

m = tv.efficientnet_b0()
m.classifier[1] = torch.nn.Linear(m.classifier[1].in_features, len(names))
m.load_state_dict(state)
m.eval()

ep = export(m, (torch.randn(1, 3, 224, 224),))
prog = to_edge_transform_and_lower(ep, partitioner=[XnnpackPartitioner()]).to_executorch()
open("model_b0.pte", "wb").write(prog.buffer)
print("classes:", names)
print("saved model_b0.pte")
