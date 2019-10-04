class Bounding_Box:
    """
    Object that represents image/video coordinates
    """

    def __init__(self, upper_left, lower_right):
        self.upper_left = upper_left
        self.lower_right = lower_right
        