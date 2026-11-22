"""
advice.py - VERDE Pi backend
Temporary lookup table that turns a predicted class into the full JSON result.
Placeholder until retrieval.py and llm.py generate real advice (weeks 4-5).
Keys are the 8 model classes. Bin names are generic UK examples; councils differ.
"""

UNCERTAIN_BELOW = 0.70  # provisional; tune it once we have labelled Pi photos

ADVICE = {
    "food_garden": {
        "name": "Food or garden waste",
        "bin": "Food waste or garden waste",
        "steps": ["Put food scraps in your food waste caddy", "Put garden waste in the garden bin or compost", "Remove any packaging, plastic ties or bags"],
        "warnings": ["Do not put cooked food in a home compost heap"],
        "why": "Food waste in landfill produces methane, but food and plant waste can be composted or turned into energy.",
    },
    "general": {
        "name": "General waste",
        "bin": "General waste",
        "steps": ["Check whether it has a special collection, such as electronics or batteries", "Otherwise place it in the general waste bin"],
        "warnings": ["Batteries and electronics should never go in the general bin"],
        "why": "Items that cannot be recycled contaminate the recycling stream if they go in the wrong bin.",
    },
    "glass": {
        "name": "Glass",
        "bin": "Glass recycling",
        "steps": ["Empty and rinse the item", "Remove lids", "Place it in the glass bin or bottle bank"],
        "warnings": ["Take care with broken glass, and wrap it before disposal"],
        "why": "Glass can be recycled again and again without losing quality.",
    },
    "metal": {
        "name": "Metal",
        "bin": "Mixed recycling",
        "steps": ["Empty the item and rinse it", "Place it in the mixed recycling bin"],
        "warnings": ["Do not put pressurised aerosols in with sharp metal or food waste"],
        "why": "Recycling metal uses far less energy than making it from raw ore.",
    },
    "paper_card": {
        "name": "Paper or cardboard",
        "bin": "Mixed recycling",
        "steps": ["Remove any tape, plastic film or plastic parts", "Flatten boxes", "Keep it dry and clean", "Place it in the mixed recycling bin"],
        "warnings": ["Greasy, food-stained or wet paper and card cannot be recycled"],
        "why": "Clean, dry paper and card can be pulped and made into new paper and packaging.",
    },
    "plastic": {
        "name": "Plastic",
        "bin": "Mixed recycling",
        "steps": ["Empty any remaining liquid", "Rinse the item quickly", "Put the lid back on", "Place it in the mixed recycling bin"],
        "warnings": ["Soft plastic films and bags often need a separate drop-off point"],
        "why": "Recycling plastic saves energy and keeps it out of landfill.",
    },
    "special": {
        "name": "Batteries or electricals",
        "bin": "Special collection point",
        "steps": ["Do not put it in household recycling or general waste", "Take batteries to a shop or council battery collection point", "Take electricals to a retailer take-back or your local recycling centre"],
        "warnings": ["Damaged or lithium batteries can cause fires, so tape the terminals and keep them dry"],
        "why": "Batteries and electronics contain hazardous and valuable materials that need specialist recycling.",
    },
    "textile": {
        "name": "Textiles",
        "bin": "Textile recycling or donation",
        "steps": ["If it is still wearable, donate it", "Otherwise take it to a textile bank", "Do not put it in household recycling"],
        "warnings": [],
        "why": "Textiles can be reused or recycled, but they jam sorting machines in household recycling.",
    },
}


def build_result(label, confidence):
    """Return the JSON contract fields for a predicted class."""
    info = ADVICE.get(label, ADVICE["general"])
    return {
        "label": info["name"],
        "confidence": round(confidence, 3),
        "uncertain": confidence < UNCERTAIN_BELOW,
        "bin": info["bin"],
        "steps": info["steps"],
        "warnings": info["warnings"],
        "why_it_matters": info["why"],
    }
