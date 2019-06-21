import os
import logging
import shutil
import main as main_node
import worker as worker_node

def main():

    envs = {}
    envs['AWS_BATCH_JOB_MAIN_NODE_INDEX'] = os.environ.get('AWS_BATCH_JOB_MAIN_NODE_INDEX', '0')
    envs['AWS_BATCH_JOB_NODE_INDEX'] = os.environ.get('AWS_BATCH_JOB_NODE_INDEX', '0')

    # set logging to log to stdout
    logging.basicConfig(level=os.environ.get('LOGLEVEL', 'INFO'))


    total, used, free = shutil.disk_usage("/")

    logging.info("Total: %d GB", (total // (2**30)))
    logging.info("Used: %d GB", (used // (2**30)))
    logging.info("Free: %d GB", (free // (2**30)))

    if envs['AWS_BATCH_JOB_MAIN_NODE_INDEX'] == envs['AWS_BATCH_JOB_NODE_INDEX']:

        logging.info("Validating main node environment variables")
        
        # validate environment variables
        envs = main_node.read_envs()
        if main_node.validate_envs(envs):

            logging.info("Starting main node")
            node = main_node.Main(envs)
            node.run()

        else:
            raise ValueError("Exception occurred when validating main node environment variables") 
        
    else:
        logging.info("Starting worker node %s", envs['AWS_BATCH_JOB_NODE_INDEX'])
        
        logging.info("Validating worker node environment variables")
        
        # validate environment variables
        envs = worker_node.read_envs()
        if worker_node.validate_envs(envs):

            logging.info("Starting worker node")
            node = worker_node.Worker(envs)
            node.run()

        else:
            raise ValueError("Exception occurred when validating worker node environment variables")

if __name__ == "__main__": main()

