"""
advice.py - VERDE Pi backend
Temporary lookup table that turns a predicted class into the full JSON result.
Placeholder until retrieval.py and llm.py generate real advice (weeks 4-5).
Bin names are generic UK examples; local councils differ.
"""

UNCERTAIN_BELOW = 0.70  # provisional; tune it once we have labelled Pi photos

ADVICE = {
    "Cardboard": {
        "bin": "Mixed recycling",
        "steps": ["Remove any tape or plastic film", "Flatten the box", "Keep it dry", "Place it in the mixed recycling bin"],
        "warnings": ["Greasy or food-stained cardboard cannot be recycled"],
        "why": "Clean, dry cardboard can be pulped and made into new packaging.",
    },
    "Food Organics": {
        "bin": "Food waste",
        "steps": ["Scrape the food into your food waste caddy", "Do not use a plastic bag unless your council allows it"],
        "warnings": [],
        "why": "Food waste in landfill produces methane, but it can be composted or turned into energy.",
    },
    "Glass": {
        "bin": "Glass recycling",
        "steps": ["Empty and rinse the item", "Remove lids", "Place it in the glass bin or bottle bank"],
        "warnings": ["Take care with broken glass, and wrap it before disposal"],
        "why": "Glass can be recycled again and again without losing quality.",
    },
    "Metal": {
        "bin": "Mixed recycling",
        "steps": ["Empty the item and rinse it", "Place it in the mixed recycling bin"],
        "warnings": ["Do not put pressurised aerosols in with sharp metal or food waste"],
        "why": "Recycling metal uses far less energy than making it from raw ore.",
    },
    "Miscellaneous Trash": {
        "bin": "General waste",
        "steps": ["Check whether it has a special collection, such as electronics", "Otherwise place it in the general waste bin"],
        "warnings": ["Batteries and electronics should go to a special recycling point"],
        "why": "Items that cannot be recycled contaminate the recycling stream if they go in the wrong bin.",
    },
    "Paper": {
        "bin": "Mixed recycling",
        "steps": ["Keep it dry and clean", "Remove any plastic parts", "Place it in the mixed recycling bin"],
        "warnings": ["Greasy or wet paper cannot be recycled"],
        "why": "Paper fibres can be reused several times when the paper stays clean.",
    },
    "Plastic": {
        "bin": "Mixed recycling",
        "steps": ["Empty any remaining liquid", "Rinse the item quickly", "Put the lid back on", "Place it in the mixed recycling bin"],
        "warnings": [],
        "why": "Recycling plastic saves energy and keeps it out of landfill.",
    },
    "Textile Trash": {
        "bin": "Textile recycling or donation",
        "steps": ["If it is still wearable, donate it", "Otherwise take it to a textile bank", "Do not put it in household recycling"],
        "warnings": [],
        "why": "Textiles can be reused or recycled, but they jam sorting machines in household recycling.",
    },
    "Vegetation": {
        "bin": "Garden waste",
        "steps": ["Place it in the garden waste bin or compost", "Remove any plastic ties or bags"],
        "warnings": [],
        "why": "Plant material composts well and returns nutrients to the soil.",
    },
}


def build_result(label, confidence):
    """Return the JSON contract fields for a predicted label."""
    info = ADVICE[label]
    return {
        "label": label,
        "confidence": round(confidence, 3),
        "uncertain": confidence < UNCERTAIN_BELOW,
        "bin": info["bin"],
        "steps": info["steps"],
        "warnings": info["warnings"],
        "why_it_matters": info["why"],
    }
